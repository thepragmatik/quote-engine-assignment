package com.bendigo.quote.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resilient HTTP client adapter implementing {@link VendorQuotePort} to interact with the
 * third-party vendor partner.
 *
 * <p><b>Resilience4j Decorator Composition & Aspect Ordering:</b> <br>
 * The default Resilience4j Spring aspect precedence is:
 *
 * <pre>
 *   Bulkhead -&gt; TimeLimiter -&gt; RateLimiter -&gt; CircuitBreaker -&gt; Retry
 * </pre>
 *
 * Since {@link CircuitBreaker} sits <i>outside</i> {@link Retry}, two methods are composed to
 * ensure exact behavioral semantics matching the system specification:
 *
 * <ol>
 *   <li>{@link #callVendorOnce(BigDecimal, int, String)} is decorated with {@link TimeLimiter}.
 *       This enforces the 2000ms deadline against <i>each individual call attempt</i> rather than
 *       the aggregated duration of all retries.
 *   <li>{@link #verify(BigDecimal, int, String)} coordinates execution under {@link Retry} and
 *       {@link CircuitBreaker}. The CircuitBreaker records exactly one logical call per incoming
 *       client request.
 *   <li>The circuit breaker fallback ({@code circuitFallback}) signature is strictly narrowed to
 *       {@link CallNotPermittedException}. This prevents the fallback from accidentally
 *       intercepting exhausted retry exceptions (which must map cleanly to HTTP 502 rather than
 *       HTTP 503).
 * </ol>
 *
 * <p><b>Underlying Socket Timeout Protection:</b> In addition to the Resilience4j {@link
 * TimeLimiter}, the underlying {@link RestClient} is configured with physical connect (1s) and read
 * (2s) timeouts. This prevents orphan thread and socket leaks when a timed-out {@link
 * CompletableFuture} is abandoned.
 */
@Component
public class VendorApiClientAdapter implements VendorQuotePort {

  private final RestClient restClient;
  private final ObjectProvider<VendorApiClientAdapter> selfProvider;

  public VendorApiClientAdapter(
      RestClient.Builder restClientBuilder,
      @Value("${vendor.api-url}") String apiUrl,
      @Value("${vendor.api-key}") String apiKey,
      @Value("${vendor.connect-timeout:1s}") Duration connectTimeout,
      @Value("${vendor.read-timeout:2s}") Duration readTimeout,
      ObjectProvider<VendorApiClientAdapter> selfProvider) {
    // Security Note: fail fast on a hostile or misconfigured VENDOR_API_URL.
    // Verifies scheme and host upfront to safeguard against SSRF or unencrypted credential egress.
    validateApiUrl(apiUrl);

    this.restClient =
        restClientBuilder
            .baseUrl(apiUrl)
            .defaultHeader("x-api-key", apiKey)
            // Resilience Note: enforce physical socket timeouts matching the TimeLimiter
            .requestFactory(httpRequestFactory(connectTimeout, readTimeout))
            .build();
    this.selfProvider = selfProvider;
  }

  private static SimpleClientHttpRequestFactory httpRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private static void validateApiUrl(String apiUrl) {
    try {
      URI uri = URI.create(apiUrl);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
      if (!Set.of("http", "https").contains(scheme)
          || uri.getHost() == null
          || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("Invalid vendor.api-url: " + apiUrl);
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Invalid vendor.api-url (must be absolute http/https URL): " + apiUrl, e);
    }
  }

  @Override
  @Retry(name = "vendor")
  @CircuitBreaker(name = "vendor", fallbackMethod = "circuitFallback")
  public VendorVerification verify(BigDecimal loanAmount, int termMonths, String riskBand) {
    try {
      // Resilience Note: dispatch through the AOP proxy provider so @TimeLimiter fires
      return selfProvider.getObject().callVendorOnce(loanAmount, termMonths, riskBand).join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      if (cause instanceof RetryableVendorException retryable) {
        throw retryable;
      }
      if (cause instanceof VendorServiceUnavailableException unavailable) {
        throw unavailable;
      }
      // Resilience Note: the raw HTTP call now has its own read timeout (2s),
      // so a slow vendor can surface here as ResourceAccessException/IOException
      // racing the TimeLimiter — map it to the retryable path either way.
      if (cause instanceof java.io.IOException
          || cause instanceof org.springframework.web.client.ResourceAccessException) {
        throw new RetryableVendorException("Vendor call timed out or network failure", cause);
      }
      if (cause instanceof TimeoutException) {
        throw new RetryableVendorException("Vendor call timed out", cause);
      }
      throw new IllegalStateException("Unexpected vendor call failure", cause);
    }
  }

  /**
   * Executes a single asynchronous HTTP call attempt to the vendor, bounded by the {@link
   * TimeLimiter}.
   */
  @TimeLimiter(name = "vendor")
  public CompletableFuture<VendorVerification> callVendorOnce(
      BigDecimal loanAmount, int termMonths, String riskBand) {
    return CompletableFuture.supplyAsync(() -> postVendorQuote(loanAmount, termMonths, riskBand));
  }

  /**
   * Fallback invoked strictly when the CircuitBreaker is in the OPEN state.
   *
   * <p>Resilience Note: The signature narrows the parameter to {@link CallNotPermittedException} so
   * that exhausted retries or network errors bubbling up from {@link #verify} are not caught here.
   */
  private VendorVerification circuitFallback(
      BigDecimal loanAmount, int termMonths, String riskBand, CallNotPermittedException ex) {
    throw new CircuitOpenException("Vendor circuit is open");
  }

  private VendorVerification postVendorQuote(
      BigDecimal loanAmount, int termMonths, String riskBand) {
    try {
      VendorVerification verification =
          restClient
              .post()
              .uri(VERIFY_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "loanAmount",
                      loanAmount,
                      "loanTermInMonths",
                      termMonths,
                      "riskBand",
                      riskBand))
              .retrieve()
              .body(VendorVerification.class);
      if (verification == null || !verification.verified()) {
        throw new VendorServiceUnavailableException("Vendor could not verify the quote");
      }
      return verification;
    } catch (HttpStatusCodeException e) {
      int status = e.getStatusCode().value();
      if (status == 500 || status == 504) {
        throw new RetryableVendorException("Vendor transient failure: HTTP " + status, e);
      }
      throw new VendorServiceUnavailableException("Vendor rejected the request: HTTP " + status, e);
    } catch (ResourceAccessException e) {
      throw new RetryableVendorException("Vendor network failure", e);
    } catch (RestClientException e) {
      // Resilience Note: RestClient wraps raw socket/connect timeouts in a plain
      // RestClientException (not a ResourceAccessException) — classify by root cause.
      if (rootCauseIsTransientIo(e)) {
        throw new RetryableVendorException("Vendor call timed out or connection failure", e);
      }
      throw new VendorServiceUnavailableException("Vendor call failed", e);
    }
  }

  private static boolean rootCauseIsTransientIo(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.ConnectException) {
        return true;
      }
    }
    return false;
  }
}
