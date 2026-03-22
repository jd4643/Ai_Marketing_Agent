package com.marketing.analytics.api;

import com.marketing.analytics.service.LearningEventService;
import com.marketing.analytics.service.OutcomeTrackingService;
import com.marketing.analytics.service.StrategyEffectivenessService;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes the closed-loop learning endpoints: recommendation outcomes,
 * strategy effectiveness, and learning events.
 */
@RestController
@RequestMapping("/analytics/feedback")
@Validated
public class FeedbackLoopController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLoopController.class);
    private final OutcomeTrackingService outcomeService;
    private final StrategyEffectivenessService effectivenessService;
    private final LearningEventService learningEventService;

    public FeedbackLoopController(OutcomeTrackingService outcomeService,
                                  StrategyEffectivenessService effectivenessService,
                                  LearningEventService learningEventService) {
        this.outcomeService = outcomeService;
        this.effectivenessService = effectivenessService;
        this.learningEventService = learningEventService;
    }

    // ─── Recommendation Outcomes ────────────────────────────────────────────

    @PostMapping("/evaluate-outcomes")
    public Map<String, Object> evaluateOutcomes() {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/feedback/evaluate-outcomes");
        return outcomeService.evaluateAllPending();
    }

    @GetMapping("/outcomes/{businessId}")
    public List<Map<String, Object>> getOutcomes(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String verdict) {
        log.info("GET /analytics/feedback/outcomes/{} verdict={}", businessId, verdict);
        return outcomeService.getOutcomes(businessId, verdict);
    }

    @GetMapping("/outcomes/{businessId}/stats")
    public Map<String, Object> getOutcomeStats(@PathVariable UUID businessId) {
        log.info("GET /analytics/feedback/outcomes/{}/stats", businessId);
        return outcomeService.getOutcomeStats(businessId);
    }

    // ─── Strategy Effectiveness ─────────────────────────────────────────────

    @PostMapping("/evaluate-strategy/{businessId}")
    public Map<String, Object> evaluateStrategy(@PathVariable UUID businessId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/feedback/evaluate-strategy/{}", businessId);
        return effectivenessService.evaluateLatestStrategy(businessId);
    }

    @GetMapping("/strategy-effectiveness/{businessId}")
    public List<Map<String, Object>> getEffectivenessHistory(@PathVariable UUID businessId) {
        log.info("GET /analytics/feedback/strategy-effectiveness/{}", businessId);
        return effectivenessService.getHistory(businessId);
    }

    @GetMapping("/strategy-freshness/{businessId}")
    public Map<String, Object> getStrategyFreshness(@PathVariable UUID businessId) {
        log.info("GET /analytics/feedback/strategy-freshness/{}", businessId);
        return effectivenessService.getLatestFreshness(businessId);
    }

    // ─── Learning Events ────────────────────────────────────────────────────

    @GetMapping("/events/{businessId}")
    public List<Map<String, Object>> getEvents(
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        log.info("GET /analytics/feedback/events/{} days={} limit={}", businessId, days, limit);
        return learningEventService.getRecentEvents(businessId, days, limit);
    }

    @GetMapping("/events/{businessId}/by-type/{eventType}")
    public List<Map<String, Object>> getEventsByType(
            @PathVariable UUID businessId,
            @PathVariable String eventType) {
        log.info("GET /analytics/feedback/events/{}/by-type/{}", businessId, eventType);
        return learningEventService.getEventsByType(businessId, eventType);
    }

    @GetMapping("/insights/{businessId}")
    public Map<String, Object> getLearningInsights(@PathVariable UUID businessId) {
        log.info("GET /analytics/feedback/insights/{}", businessId);
        return learningEventService.getLearningInsights(businessId);
    }
}
