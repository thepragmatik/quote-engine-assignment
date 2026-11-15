package com.bendigo.quote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web security and CORS configuration satisfying MISSION_v2 Section 6: "Frontend-to-Backend: Strict
 * input sanitization, Zod schema validation on entry, CORS origin restriction, and standard browser
 * security headers (CSP, X-Frame-Options, HSTS)."
 *
 * <p>ATTENTION(security): Even though production browser traffic routes through the same-origin
 * nginx reverse proxy, the backend REST API directly enforces CORS origin restrictions so that
 * unauthorized external web origins cannot issue cross-origin requests to port 8000.
 */
@Configuration
public class WebSecurityCorsConfig implements WebMvcConfigurer {

  private final String allowedOrigins;

  public WebSecurityCorsConfig(
      @Value("${security.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
          String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins.split(","))
        .allowedMethods("POST", "OPTIONS")
        .allowedHeaders("Content-Type", "Accept")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
