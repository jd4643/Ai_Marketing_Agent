package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.AdPlatformIntegrationService;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService.RecommendationResult;
import com.marketing.analytics.service.RecommendationActionService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class CreativeAssetPerformanceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository repo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;

    @Test void ingestValidation() throws Exception {
        mvc.perform(post("/analytics/creative-assets/metrics/ingest")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void summaryOk() throws Exception {
        when(repo.aggregateByAsset(any(), any())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/summary")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assets").isArray());
    }

    @Test void winnersOk() throws Exception {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/winners")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.winners").isArray())
            .andExpect(jsonPath("$.thresholds").exists());
    }

    @Test void losersOk() throws Exception {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/losers")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.losers").isArray());
    }

    @Test void testingOk() throws Exception {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/testing")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.testing").isArray());
    }

    @Test void scorecardOk() throws Exception {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/scorecard")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAssets").value(0))
            .andExpect(jsonPath("$.winners").value(0))
            .andExpect(jsonPath("$.weak").value(0));
    }

    @Test void recommendationsOk() throws Exception {
        when(recommendationService.generateAndPersist(any(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/recommendations")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendations").isArray())
            .andExpect(jsonPath("$.count").value(0));
    }

    @Test void insightsOk() throws Exception {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/insights")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAssets").value(0))
            .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test void winnersWithData() throws Exception {
        UUID assetId = UUID.randomUUID();
        ScoringResult winner = new ScoringResult(
                assetId, "meta", "WINNER",
                BigDecimal.valueOf(85), BigDecimal.valueOf(0.92),
                Map.of("volumeSufficient", true, "signalConsistency", 0.9),
                5000L, 200L, 10L,
                BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                3.0, 0.04, 2.5, 50.0,
                "{\"hook\":\"Stop scrolling\"}", "image", null
        );
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(winner));
        mvc.perform(get("/analytics/creative-assets/winners")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.winners[0].classification").value("WINNER"))
            .andExpect(jsonPath("$.winners[0].performanceScore").value(85))
            .andExpect(jsonPath("$.winners[0].hook").value("Stop scrolling"));
    }

    @Test void losersWithData() throws Exception {
        UUID assetId = UUID.randomUUID();
        ScoringResult loser = new ScoringResult(
                assetId, "meta", "WEAK",
                BigDecimal.valueOf(15), BigDecimal.valueOf(0.80),
                Map.of("volumeSufficient", true, "signalConsistency", 0.5),
                4000L, 50L, 1L,
                BigDecimal.valueOf(800), BigDecimal.valueOf(200),
                0.25, 0.0125, 16.0, 800.0,
                null, "image", null
        );
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(loser));
        mvc.perform(get("/analytics/creative-assets/losers")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.losers[0].classification").value("WEAK"));
    }

    @Test void scorecardWithMixedData() throws Exception {
        List<ScoringResult> mixed = List.of(
                new ScoringResult(UUID.randomUUID(), "meta", "WINNER",
                        BigDecimal.valueOf(85), BigDecimal.valueOf(0.92), Map.of(),
                        5000L, 200L, 10L,
                        BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                        3.0, 0.04, 2.5, 50.0,
                        null, "image", null),
                new ScoringResult(UUID.randomUUID(), "meta", "WEAK",
                        BigDecimal.valueOf(15), BigDecimal.valueOf(0.80), Map.of(),
                        4000L, 50L, 1L,
                        BigDecimal.valueOf(800), BigDecimal.valueOf(200),
                        0.25, 0.0125, 16.0, 800.0,
                        null, "video", null),
                new ScoringResult(UUID.randomUUID(), "google", "TESTING",
                        BigDecimal.valueOf(50), BigDecimal.valueOf(0.60), Map.of(),
                        1500L, 30L, 2L,
                        BigDecimal.valueOf(200), BigDecimal.valueOf(300),
                        1.5, 0.02, 6.67, 100.0,
                        null, "image", null)
        );
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(mixed);
        mvc.perform(get("/analytics/creative-assets/scorecard")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAssets").value(3))
            .andExpect(jsonPath("$.winners").value(1))
            .andExpect(jsonPath("$.weak").value(1))
            .andExpect(jsonPath("$.testing").value(1));
    }

    @Test void recommendationsWithData() throws Exception {
        UUID assetId = UUID.randomUUID();
        RecommendationResult rec = new RecommendationResult(
                "SCALE", "HIGH", "Scale top performer",
                "This asset has ROAS 3.0 — increase daily budget by 30%",
                assetId, Map.of("avgRoas", 3.0, "impressions", 5000),
                Map.of("classification", "WINNER"), "Increase budget on this campaign"
        );
        when(recommendationService.generateAndPersist(any(), anyInt())).thenReturn(List.of(rec));
        mvc.perform(get("/analytics/creative-assets/recommendations")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.recommendations[0].recommendationType").value("SCALE"))
            .andExpect(jsonPath("$.recommendations[0].priority").value("HIGH"));
    }

    @Test void insightsWithBreakdown() throws Exception {
        List<ScoringResult> data = List.of(
                new ScoringResult(UUID.randomUUID(), "meta", "WINNER",
                        BigDecimal.valueOf(85), BigDecimal.valueOf(0.92), Map.of(),
                        5000L, 200L, 10L,
                        BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                        3.0, 0.04, 2.5, 50.0,
                        null, "image", null),
                new ScoringResult(UUID.randomUUID(), "meta", "INSUFFICIENT_DATA",
                        BigDecimal.ZERO, BigDecimal.valueOf(0.10), Map.of(),
                        500L, 5L, 0L,
                        BigDecimal.valueOf(100), BigDecimal.ZERO,
                        0.0, 0.01, 20.0, 0.0,
                        null, "video", null)
        );
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(data);
        mvc.perform(get("/analytics/creative-assets/insights")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAssets").value(2))
            .andExpect(jsonPath("$.breakdown.winners").value(1))
            .andExpect(jsonPath("$.breakdown.insufficientData").value(1))
            .andExpect(jsonPath("$.bestRoas").value(3.0));
    }

    @Test void recomputeOk() throws Exception {
        when(scoringService.recomputeClassifications(any(), anyInt())).thenReturn(5);
        mvc.perform(post("/analytics/creative-assets/recompute")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowsUpdated").value(5));
    }

    @Test void classifyDelegation() throws Exception {
        when(scoringService.classify(5000L, 200L, 10L, 3.0)).thenReturn("WINNER");
        when(scoringService.classify(500L, 5L, 0L, 0.0)).thenReturn("INSUFFICIENT_DATA");
        when(scoringService.classify(4000L, 50L, 1L, 0.5)).thenReturn("WEAK");
        when(scoringService.classify(3500L, 120L, 5L, 1.5)).thenReturn("TESTING");

        when(scoringService.computePerformanceScore(anyLong(), anyLong(), anyLong(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(BigDecimal.valueOf(50));
        when(scoringService.computeConfidence(anyLong(), anyLong(), anyLong(), anyDouble(), anyDouble())).thenReturn(BigDecimal.valueOf(0.5));
        when(scoringService.buildReasoning(anyLong(), anyLong(), anyLong(), anyDouble(), any())).thenReturn(Map.of());

        String body = """
            {
                "creativeAssetId": "%s",
                "businessId": "%s",
                "platform": "meta",
                "impressions": 5000,
                "clicks": 200,
                "conversions": 10,
                "spend": 500.0,
                "revenue": 1500.0,
                "ctr": 0.04,
                "cpc": 2.5,
                "cpa": 50.0,
                "roas": 3.0,
                "recordedAt": "2024-01-01T00:00:00Z"
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/analytics/creative-assets/metrics/ingest")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.classification").value("WINNER"));
    }
}
