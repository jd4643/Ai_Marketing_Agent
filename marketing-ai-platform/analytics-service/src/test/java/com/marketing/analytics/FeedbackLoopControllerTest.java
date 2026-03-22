package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.*;
import com.marketing.common.GlobalExceptionHandler;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(GlobalExceptionHandler.class)
class FeedbackLoopControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository perfRepo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;
    @MockBean DashboardAggregationService dashboardService;
    @MockBean ExecutionService executionService;
    @MockBean SnapshotService snapshotService;
    @MockBean OutcomeTrackingService outcomeTrackingService;
    @MockBean LearningEventService learningEventService;
    @MockBean StrategyEffectivenessService strategyEffectivenessService;

    static final UUID BIZ = UUID.randomUUID();

    // ─── Outcome Endpoints ──────────────────────────────────────────────

    @Test void evaluateOutcomesOk() throws Exception {
        when(outcomeTrackingService.evaluateAllPending())
                .thenReturn(Map.of("evaluated", 3, "positive", 2, "negative", 1, "neutral", 0));

        mvc.perform(post("/analytics/feedback/evaluate-outcomes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluated").value(3))
                .andExpect(jsonPath("$.positive").value(2));
    }

    @Test void getOutcomesOk() throws Exception {
        when(outcomeTrackingService.getOutcomes(eq(BIZ), isNull()))
                .thenReturn(List.of(Map.of("recommendationId", UUID.randomUUID().toString(), "outcomeVerdict", "POSITIVE")));

        mvc.perform(get("/analytics/feedback/outcomes/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcomeVerdict").value("POSITIVE"));
    }

    @Test void getOutcomesWithVerdictFilter() throws Exception {
        when(outcomeTrackingService.getOutcomes(eq(BIZ), eq("NEGATIVE")))
                .thenReturn(List.of(Map.of("outcomeVerdict", "NEGATIVE")));

        mvc.perform(get("/analytics/feedback/outcomes/{id}", BIZ).param("verdict", "NEGATIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcomeVerdict").value("NEGATIVE"));
    }

    @Test void getOutcomeStatsOk() throws Exception {
        when(outcomeTrackingService.getOutcomeStats(BIZ))
                .thenReturn(Map.of("businessId", BIZ, "totalOutcomes", 5, "successRate", 60.0));

        mvc.perform(get("/analytics/feedback/outcomes/{id}/stats", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutcomes").value(5))
                .andExpect(jsonPath("$.successRate").value(60.0));
    }

    // ─── Strategy Effectiveness Endpoints ───────────────────────────────

    @Test void evaluateStrategyOk() throws Exception {
        when(strategyEffectivenessService.evaluateLatestStrategy(BIZ))
                .thenReturn(Map.of("businessId", BIZ, "freshnessScore", 75.0, "recommendedAction", "KEEP"));

        mvc.perform(post("/analytics/feedback/evaluate-strategy/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshnessScore").value(75.0))
                .andExpect(jsonPath("$.recommendedAction").value("KEEP"));
    }

    @Test void getEffectivenessHistoryOk() throws Exception {
        when(strategyEffectivenessService.getHistory(BIZ))
                .thenReturn(List.of(Map.of("freshnessScore", 80.0, "recommendedAction", "KEEP")));

        mvc.perform(get("/analytics/feedback/strategy-effectiveness/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].freshnessScore").value(80.0));
    }

    @Test void getStrategyFreshnessOk() throws Exception {
        when(strategyEffectivenessService.getLatestFreshness(BIZ))
                .thenReturn(Map.of("freshnessScore", 65.0, "recommendedAction", "REFRESH"));

        mvc.perform(get("/analytics/feedback/strategy-freshness/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshnessScore").value(65.0))
                .andExpect(jsonPath("$.recommendedAction").value("REFRESH"));
    }

    @Test void getStrategyFreshnessNoEvaluation() throws Exception {
        when(strategyEffectivenessService.getLatestFreshness(BIZ))
                .thenReturn(Map.of("freshnessScore", -1, "recommendedAction", "EVALUATE"));

        mvc.perform(get("/analytics/feedback/strategy-freshness/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshnessScore").value(-1))
                .andExpect(jsonPath("$.recommendedAction").value("EVALUATE"));
    }

    // ─── Learning Events Endpoints ──────────────────────────────────────

    @Test void getEventsOk() throws Exception {
        when(learningEventService.getRecentEvents(eq(BIZ), eq(30), eq(50)))
                .thenReturn(List.of(
                        Map.of("eventType", "BASELINE_CAPTURED", "severity", "INFO"),
                        Map.of("eventType", "RECOMMENDATION_OUTCOME", "severity", "INFO")
                ));

        mvc.perform(get("/analytics/feedback/events/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventType").value("BASELINE_CAPTURED"));
    }

    @Test void getEventsCustomParams() throws Exception {
        when(learningEventService.getRecentEvents(eq(BIZ), eq(7), eq(10)))
                .thenReturn(List.of());

        mvc.perform(get("/analytics/feedback/events/{id}", BIZ)
                .param("days", "7")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test void getEventsByTypeOk() throws Exception {
        when(learningEventService.getEventsByType(BIZ, "STRATEGY_STALE"))
                .thenReturn(List.of(Map.of("eventType", "STRATEGY_STALE", "severity", "WARNING")));

        mvc.perform(get("/analytics/feedback/events/{id}/by-type/{type}", BIZ, "STRATEGY_STALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("STRATEGY_STALE"));
    }

    @Test void getLearningInsightsOk() throws Exception {
        when(learningEventService.getLearningInsights(BIZ))
                .thenReturn(Map.of(
                        "businessId", BIZ,
                        "totalEvents30d", 15,
                        "warnings30d", 2,
                        "outcomesLast7d", 3L
                ));

        mvc.perform(get("/analytics/feedback/insights/{id}", BIZ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents30d").value(15))
                .andExpect(jsonPath("$.warnings30d").value(2));
    }
}
