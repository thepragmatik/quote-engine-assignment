package com.bendigo.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Automated Contract Drift & Schema Synchronization Test.
 *
 * <p>Validates that the server-side {@link QuoteRequest} and {@link QuoteResponse} DTO records
 * adhere strictly to the authoritative OpenAPI specification in {@code .github/specs/openapi.yaml}.
 *
 * <p>If anyone alters boundary constraints, property names, or enum sets in the OpenAPI spec
 * without updating the Java records (or vice versa), this test immediately fails at build time,
 * guaranteeing bidirectional contract alignment across both frontend and backend.
 */
@DisplayName("OpenApiContractDriftTest — Contract-First Spec Synchronization")
class OpenApiContractDriftTest {

  private static Map<String, Object> openApiMap;
  private static Map<String, Object> schemas;

  @BeforeAll
  @SuppressWarnings("unchecked")
  static void loadOpenApiSpec() throws Exception {
    File specFile = new File("../.github/specs/openapi.yaml");
    if (!specFile.exists()) {
      specFile = new File(".github/specs/openapi.yaml");
    }

    InputStream in = null;
    if (specFile.exists()) {
      in = new FileInputStream(specFile);
    } else {
      in = OpenApiContractDriftTest.class.getResourceAsStream("/specs/openapi.yaml");
    }

    // If running in an isolated build container where only the backend context was copied,
    // gracefully skip rather than failing the container build.
    org.junit.jupiter.api.Assumptions.assumeTrue(
        in != null,
        "OpenAPI specification (.github/specs/openapi.yaml) not available in build environment.");

    try (InputStream stream = in) {
      openApiMap = new Yaml().load(stream);
    }

    Map<String, Object> components = (Map<String, Object>) openApiMap.get("components");
    assertThat(components).isNotNull();
    schemas = (Map<String, Object>) components.get("schemas");
    assertThat(schemas).isNotNull();
  }

  @Test
  @DisplayName(
      "QuoteRequest record components and validation constraints match OpenAPI QuoteRequest schema")
  @SuppressWarnings("unchecked")
  void quoteRequestMatchesOpenApiSchema() {
    Map<String, Object> quoteRequestSchema = (Map<String, Object>) schemas.get("QuoteRequest");
    assertThat(quoteRequestSchema).isNotNull();

    List<String> requiredFields = (List<String>) quoteRequestSchema.get("required");
    assertThat(requiredFields)
        .containsExactlyInAnyOrder("loanAmount", "loanTermInMonths", "riskBand");

    Map<String, Object> properties = (Map<String, Object>) quoteRequestSchema.get("properties");
    assertThat(properties).isNotNull();

    // 1. Verify loanAmount constraints
    Map<String, Object> loanAmountProp = (Map<String, Object>) properties.get("loanAmount");
    Number minAmount = (Number) loanAmountProp.get("minimum");
    Number maxAmount = (Number) loanAmountProp.get("maximum");

    assertThat(getAnnotation(QuoteRequest.class, "loanAmount", NotNull.class)).isNotNull();
    DecimalMin decimalMin = getAnnotation(QuoteRequest.class, "loanAmount", DecimalMin.class);
    assertThat(decimalMin).isNotNull();
    assertThat(decimalMin.value()).isEqualTo(String.valueOf(minAmount.longValue()));
    DecimalMax decimalMax = getAnnotation(QuoteRequest.class, "loanAmount", DecimalMax.class);
    assertThat(decimalMax).isNotNull();
    assertThat(decimalMax.value()).isEqualTo(String.valueOf(maxAmount.longValue()));

    // 2. Verify loanTermInMonths constraints
    Map<String, Object> termProp = (Map<String, Object>) properties.get("loanTermInMonths");
    Number minTerm = (Number) termProp.get("minimum");
    Number maxTerm = (Number) termProp.get("maximum");

    assertThat(getAnnotation(QuoteRequest.class, "loanTermInMonths", NotNull.class)).isNotNull();
    Min minAnnotation = getAnnotation(QuoteRequest.class, "loanTermInMonths", Min.class);
    assertThat(minAnnotation).isNotNull();
    assertThat(minAnnotation.value()).isEqualTo(minTerm.longValue());
    Max maxAnnotation = getAnnotation(QuoteRequest.class, "loanTermInMonths", Max.class);
    assertThat(maxAnnotation).isNotNull();
    assertThat(maxAnnotation.value()).isEqualTo(maxTerm.longValue());

    // 3. Verify riskBand enum constraints
    Map<String, Object> riskBandProp = (Map<String, Object>) properties.get("riskBand");
    List<String> allowedBands = (List<String>) riskBandProp.get("enum");
    assertThat(allowedBands).containsExactly("LOW", "MEDIUM", "HIGH");

    assertThat(getAnnotation(QuoteRequest.class, "riskBand", NotNull.class)).isNotNull();
    Pattern pattern = getAnnotation(QuoteRequest.class, "riskBand", Pattern.class);
    assertThat(pattern).isNotNull();
    String expectedPattern = String.join("|", allowedBands);
    assertThat(pattern.regexp()).isEqualTo(expectedPattern);
  }

  @Test
  @DisplayName("QuoteResponse record components match OpenAPI QuoteResponse schema properties")
  @SuppressWarnings("unchecked")
  void quoteResponseMatchesOpenApiSchema() {
    Map<String, Object> quoteResponseSchema = (Map<String, Object>) schemas.get("QuoteResponse");
    assertThat(quoteResponseSchema).isNotNull();

    Map<String, Object> properties = (Map<String, Object>) quoteResponseSchema.get("properties");
    assertThat(properties).isNotNull();
    assertThat(properties.keySet())
        .containsExactlyInAnyOrder("quoteId", "commissionRate", "totalCommission");

    List<String> recordNames =
        Arrays.stream(QuoteResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    assertThat(recordNames)
        .containsExactlyInAnyOrder("quoteId", "commissionRate", "totalCommission");
  }

  private static <T extends Annotation> T getAnnotation(
      Class<?> recordClass, String name, Class<T> annotationClass) {
    RecordComponent component =
        Arrays.stream(recordClass.getRecordComponents())
            .filter(c -> c.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Record component not found: " + name));

    T ann = component.getAnnotation(annotationClass);
    if (ann != null) {
      return ann;
    }
    ann = component.getAccessor().getAnnotation(annotationClass);
    if (ann != null) {
      return ann;
    }
    try {
      return recordClass.getDeclaredField(name).getAnnotation(annotationClass);
    } catch (NoSuchFieldException e) {
      return null;
    }
  }
}
