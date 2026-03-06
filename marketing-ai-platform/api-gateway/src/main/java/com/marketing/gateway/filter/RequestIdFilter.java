package com.marketing.gateway.filter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {
  @Override public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange, GatewayFilterChain chain){
    String reqId=exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if(reqId==null||reqId.isBlank()) reqId=UUID.randomUUID().toString();
    ServerHttpRequest req=exchange.getRequest().mutate().header("X-Request-Id",reqId).build();
    MDC.put("requestId",reqId);
    return chain.filter(exchange.mutate().request(req).build()).doFinally(s->MDC.remove("requestId"));
  }
  @Override public int getOrder(){return -1;}
}
