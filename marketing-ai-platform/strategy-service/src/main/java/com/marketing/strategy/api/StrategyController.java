package com.marketing.strategy.api;

import com.marketing.strategy.service.StrategyService;
import com.marketing.strategy.model.*;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/strategy")
@Validated
public class StrategyController {
    private static final ConcurrentHashMap<UUID, java.util.ArrayDeque<Long>> RATE = new ConcurrentHashMap<>();

    private void enforceRate(UUID businessId) {
        long now = Instant.now().getEpochSecond();
        var q = RATE.computeIfAbsent(businessId, k -> new java.util.ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60) q.pollFirst();
            if (q.size() >= 30) throw new IllegalArgumentException("Rate limit exceeded for businessId");
            q.addLast(now);
        }
    }

    private final StrategyService service;

    public StrategyController(StrategyService s) {
        this.service = s;
    }

    public record GenerateRequest(@NotNull UUID businessId, @NotBlank String objective,
                                  @NotNull @DecimalMin("1.0") BigDecimal monthlyBudget, List<String> trends,
                                  String notes) {
    }

    @PostMapping("/generate")
    public StrategyResponse generate(@Valid @RequestBody GenerateRequest req, @RequestHeader("X-Request-Id") UUID requestId) {
        enforceRate(req.businessId());
        return service.generate(new StrategyRequest(req.businessId(), req.objective(), req.monthlyBudget(), req.trends(), req.notes()), requestId);
    }

    @GetMapping("/{requestId}")
    public StrategyResponse get(@PathVariable UUID requestId) {
        return service.getByRequestId(requestId);
    }

    @GetMapping("/history")
    public List<HistorySummary> history(@RequestParam UUID businessId, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.history(businessId, limit);
    }

    @GetMapping("/intel/history")
    public List<StrategyIntelSummary> intelHistory(@RequestParam UUID businessId, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.intelHistory(businessId, limit);
    }
}
