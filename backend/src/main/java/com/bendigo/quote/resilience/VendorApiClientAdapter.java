package com.bendigo.quote.resilience;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

/**
 * Resilience-decorated adapter for the vendor verification API.
 *
 * <p>ATTENTION(resilience): two composed methods keep the aspect nesting correct. The default
 * aspect order is Bulkhead -> TimeLimiter -> RateLimiter -> CircuitBreaker -> Retry (innermost
 * last), so the CircuitBreaker wraps the WHOLE retry chain: it records ONE logical call per
 * request and its fallback sees exhausted retries and CB rejections alike. The fallback signature
 * is deliberately NARROWED to CallNotPermittedException — a wider Throwable would also swallow
 * exhausted-retry exceptions and wrongly answer 503 instead of 502.
 */
@Component
public class VendorApiClientAdapter implements VendorQuotePort {

    private static final String VERIFY_PATH = "/api/v1/vendor/quotes";

    private final RestClient restClient;
    private final ObjectProvider<VendorApiClientAdapter> selfProvider;

    public VendorApiClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${vendor.api-url}") String apiUrl,
            @Value("${vendor.api-key}") String apiKey,
            ObjectProvider<VendorApiClientAdapter> selfProvider) {
        this.restClient =
                restClientBuilder
                        .baseUrl(apiUrl)
                        .defaultHeader("x-api-key", apiKey)
                        .build();
        this.selfProvider = selfProvider;
    }

    @Override
    @Retry(name = "vendor")
    @CircuitBreaker(name = "vendor", fallbackMethod = "circuitFallback")
    public VendorVerification verify(BigDecimal loanAmount, int termMonths, String riskBand) {
        try {
            // ATTENTION(resilience): call through the AOP proxy so the @TimeLimiter aspect fires;
            // a plain this.callVendorOnce(...) self-invocation would bypass it.
            return selfProvider.getObject().callVendorOnce(loanAmount, termMonths, riskBand).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RetryableVendorException retryable) {
                throw retryable;
            }
            if (cause instanceof VendorServiceUnavailableException unavailable) {
                throw unavailable;
            }
            if (cause instanceof TimeoutException) {
                throw new RetryableVendorException("Vendor call timed out", cause);
            }
            throw new IllegalStateException("Unexpected vendor call failure", cause);
        }
    }

    // ATTENTION(resilience): the TimeLimiter bounds ONE raw HTTP attempt (2s), not the retry chain.
    // Runs the blocking call on a worker thread so the limiter can time out the future.
    @TimeLimiter(name = "vendor")
    public CompletableFuture<VendorVerification> callVendorOnce(
            BigDecimal loanAmount, int termMonths, String riskBand) {
        return CompletableFuture.supplyAsync(() -> postVendorQuote(loanAmount, termMonths, riskBand));
    }

    // ATTENTION(resilience): narrowed fallback — only CB-rejected calls land here and become 503;
    // exhausted retries propagate as RetryableVendorException and are mapped to 502.
    private VendorVerification circuitFallback(
            BigDecimal loanAmount, int termMonths, String riskBand, CallNotPermittedException ex) {
        throw new CircuitOpenException("Vendor circuit is open");
    }

    private VendorVerification postVendorQuote(BigDecimal loanAmount, int termMonths, String riskBand) {
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
        }
    }
}
