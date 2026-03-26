package com.marketing.creative.api;
import com.marketing.creative.service.CreativeService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;import jakarta.validation.constraints.*;import java.util.*;import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/creative") @Validated
public class CreativeController {
 private static final ConcurrentHashMap<UUID, java.util.ArrayDeque<Long>> RATE=new ConcurrentHashMap<>();
 private void enforceRate(UUID businessId){
  long now=Instant.now().getEpochSecond();
  var q=RATE.computeIfAbsent(businessId,k->new java.util.ArrayDeque<>());
  synchronized(q){ while(!q.isEmpty() && now-q.peekFirst()>60) q.pollFirst(); if(q.size()>=30) throw new IllegalArgumentException("Rate limit exceeded for businessId"); q.addLast(now);} }
 private final CreativeService service; public CreativeController(CreativeService s){service=s;}
 public record GenerateRequest(@NotNull UUID businessId,@NotBlank String platform,@NotBlank String format,@NotBlank String objective,UUID strategyRequestId,List<String> trendsOverride){}
 @PostMapping("/generate") public Map<String,Object> generate(@Valid @RequestBody GenerateRequest req,@RequestHeader("X-Request-Id") UUID requestId){enforceRate(req.businessId());
    return service.generate(req,requestId);}
 @GetMapping("/history/{businessId}") public List<Map<String,Object>> history(@PathVariable UUID businessId){return service.history(businessId);}
}
