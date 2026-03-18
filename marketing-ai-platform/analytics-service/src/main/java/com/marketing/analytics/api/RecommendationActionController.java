package com.marketing.analytics.api;

import com.marketing.analytics.service.RecommendationActionService;

import jakarta.validation.constraints.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Recommendation Action Layer endpoints.
 * Provides apply/dismiss lifecycle, listing, detail, dashboard grouping, and export launch package.
 */
@RestController
@RequestMapping("/analytics/recommendations")
@Validated
public class RecommendationActionController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationActionController.class);
    private final RecommendationActionService actionService;

    public RecommendationActionController(RecommendationActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * Mark a recommendation as APPLIED. Idempotent.
     */
    @PostMapping("/{recommendationId}/apply")
    public Map<String, Object> apply(@PathVariable UUID recommendationId) {
        log.info("Apply recommendation recommendationId={}", recommendationId);
        return actionService.apply(recommendationId);
    }

    /**
     * Mark a recommendation as DISMISSED. Idempotent.
     */
    @PostMapping("/{recommendationId}/dismiss")
    public Map<String, Object> dismiss(@PathVariable UUID recommendationId) {
        log.info("Dismiss recommendation recommendationId={}", recommendationId);
        return actionService.dismiss(recommendationId);
    }

    /**
     * Get full detail for a single recommendation with available actions.
     */
    @GetMapping("/{recommendationId}")
    public Map<String, Object> detail(@PathVariable UUID recommendationId) {
        return actionService.getDetail(recommendationId);
    }

    /**
     * List recommendations for a business with optional status filter and time window.
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam UUID businessId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        List<Map<String, Object>> recs = actionService.list(businessId, status, days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);
        result.put("status", status);
        result.put("days", days);
        result.put("count", recs.size());
        result.put("recommendations", recs);
        return result;
    }

    /**
     * Dashboard-friendly recommendations grouped by priority with action cards.
     */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam UUID businessId) {
        return actionService.dashboard(businessId);
    }

    /**
     * Export a launch-ready package from a recommendation.
     */
    @GetMapping("/{recommendationId}/export-launch-package")
    public Map<String, Object> exportLaunchPackage(
            @RequestParam UUID businessId,
            @PathVariable UUID recommendationId) {
        log.info("Export launch package businessId={} recommendationId={}", businessId, recommendationId);
        return actionService.exportLaunchPackage(businessId, recommendationId);
    }
}
