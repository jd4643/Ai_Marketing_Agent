package com.marketing.gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
  private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Value("${gateway.rate-limit-per-minute:60}")
  int rateLimit;

  @PostConstruct
  void init() {
    log.info("gateway_config rateLimit={}", rateLimit);
  }

  @Bean
  RouteLocator routes(RouteLocatorBuilder b) {
    return b.routes()
        .route("strategy",
            r -> r.path("/strategy/**").filters(f -> f.filter((ex, chain) -> {
              String ip = ex.getRequest().getRemoteAddress() != null
                  ? ex.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
              Bucket bucket = buckets.computeIfAbsent(ip,
                  k -> Bucket.builder().addLimit(
                      Bandwidth.classic(rateLimit,
                          Refill.intervally(rateLimit, Duration.ofMinutes(1)))).build());
              if (!bucket.tryConsume(1)) {
                ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                String reqId = ex.getRequest().getHeaders().getFirst("X-Request-Id");
                if (reqId == null) {
                  reqId = UUID.randomUUID().toString();
                }
                String body = "{\"requestId\":\"" + reqId
                    + "\",\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\",\"details\":{}}";
                return ex.getResponse().writeWith(
                    Mono.just(ex.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
              }
              return chain.filter(ex);
            })).uri("http://strategy-service:8081"))
        .route("creative", r -> r.path("/creative/**").uri("http://creative-service:8082"))
        .route("analytics", r -> r.path("/analytics/**").uri("http://analytics-service:8083"))
        .route("trends", r -> r.path("/trends/**").uri("http://trend-service:8091"))
        .route("generate", r -> r.path("/generate/**").uri("http://generation-service:8092"))
        .build();
  }

  @Bean
  CorsWebFilter corsFilter() {
    CorsConfiguration c = new CorsConfiguration();
    c.addAllowedOriginPattern("*");
    c.addAllowedHeader("*");
    c.addAllowedMethod("*");
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return new CorsWebFilter(s);
  }
}
