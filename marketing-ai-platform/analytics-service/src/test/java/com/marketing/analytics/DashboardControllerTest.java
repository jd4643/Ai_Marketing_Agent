package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.dashboard.DashboardDtos.*;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.*;
import com.marketing.common.GlobalExceptionHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository perfRepo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;
    @MockBean DashboardAggregationService dashboardService;

    static final UUID BIZ = UUID.randomUUID();

    // ─── Overview ───────────────────────────────────────────────────────

    @Test void overviewOk() throws Exception {
        when(dashboardService.getOverview(eq(BIZ), eq(30))).thenReturn(
                new OverviewResponse(BIZ, "Acme Corp", "retail", 30,
                        new SpendSummary(bd(1000), bd(5000), bd(5), 50000, 2000, 100),
                        new CreativeHealth(10, 3, 4, 2, 1),
                        5,
                        new TopSignals("meta", "image", "Limited time offer"),
                        List.of(new SyncStatus("META", UUID.randomUUID(), "My Ad Account", "ACTIVE",
                                Instant.now()))));

        mvc.perform(get("/analytics/dashboard/overview").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(BIZ.toString()))
                .andExpect(jsonPath("$.businessName").value("Acme Corp"))
                .andExpect(jsonPath("$.summary.totalSpend").value(1000))
                .andExpect(jsonPath("$.summary.overallRoas").value(5))
                .andExpect(jsonPath("$.creativeHealth.winners").value(3))
                .andExpect(jsonPath("$.openRecommendations").value(5))
                .andExpect(jsonPath("$.topSignals.bestPlatform").value("meta"))
                .andExpect(jsonPath("$.syncStatus[0].platform").value("META"));
    }

    @Test void overviewWithCustomDays() throws Exception {
        when(dashboardService.getOverview(eq(BIZ), eq(7))).thenReturn(
                new OverviewResponse(BIZ, null, null, 7,
                        SpendSummary.EMPTY, CreativeHealth.EMPTY, 0, TopSignals.EMPTY, List.of()));

        mvc.perform(get("/analytics/dashboard/overview")
                        .param("businessId", BIZ.toString())
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.summary.totalSpend").value(0))
                .andExpect(jsonPath("$.creativeHealth.totalAssets").value(0));
    }

    @Test void overviewMissingBusinessId() throws Exception {
        mvc.perform(get("/analytics/dashboard/overview"))
                .andExpect(status().isInternalServerError());
    }

    @Test void overviewDaysOutOfRange() throws Exception {
        mvc.perform(get("/analytics/dashboard/overview")
                        .param("businessId", BIZ.toString())
                        .param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    // ─── Creatives ──────────────────────────────────────────────────────

    @Test void creativesOk() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(dashboardService.getCreatives(eq(BIZ), isNull(), isNull(), eq(50))).thenReturn(
                new CreativesResponse(BIZ, 1, List.of(
                        new CreativeCard(assetId, "meta", "image", "WINNER",
                                bd(75), bd(0.85), 5000, 200, 15,
                                bd(500), bd(3000), 6.0, 0.04,
                                "Limited time offer", "Create an ad"))));

        mvc.perform(get("/analytics/dashboard/creatives").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.creatives[0].classification").value("WINNER"))
                .andExpect(jsonPath("$.creatives[0].avgRoas").value(6.0));
    }

    @Test void creativesWithFilters() throws Exception {
        when(dashboardService.getCreatives(eq(BIZ), eq("WINNER"), eq("meta"), eq(10))).thenReturn(
                new CreativesResponse(BIZ, 0, List.of()));

        mvc.perform(get("/analytics/dashboard/creatives")
                        .param("businessId", BIZ.toString())
                        .param("status", "WINNER")
                        .param("platform", "meta")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void creativesMissingBusinessId() throws Exception {
        mvc.perform(get("/analytics/dashboard/creatives"))
                .andExpect(status().isInternalServerError());
    }

    // ─── Recommendations ────────────────────────────────────────────────

    @Test void recommendationsOk() throws Exception {
        UUID recId = UUID.randomUUID();
        when(dashboardService.getRecommendations(eq(BIZ), eq("OPEN"))).thenReturn(
                new RecommendationsResponse(BIZ, 1,
                        List.of(new RecommendationCard(recId, "Scale top performer", "SCALE", "HIGH", "OPEN",
                                "Strong ROAS", UUID.randomUUID(), "Increase budget",
                                List.of("APPLY", "DISMISS"), Instant.now())),
                        List.of(), List.of()));

        mvc.perform(get("/analytics/dashboard/recommendations").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.highPriority[0].title").value("Scale top performer"))
                .andExpect(jsonPath("$.highPriority[0].availableActions[0]").value("APPLY"));
    }

    @Test void recommendationsNoFilter() throws Exception {
        when(dashboardService.getRecommendations(eq(BIZ), eq("OPEN"))).thenReturn(
                new RecommendationsResponse(BIZ, 0, List.of(), List.of(), List.of()));

        mvc.perform(get("/analytics/dashboard/recommendations")
                        .param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ─── Strategy ───────────────────────────────────────────────────────

    @Test void strategyOk() throws Exception {
        when(dashboardService.getStrategy(eq(BIZ))).thenReturn(
                new StrategyResponse(BIZ, "Acme Corp", "retail", "millennials",
                        new SpendSummary(bd(2000), bd(8000), bd(4), 100000, 4000, 200),
                        new CreativeHealth(20, 5, 8, 4, 3),
                        List.of()));

        mvc.perform(get("/analytics/dashboard/strategy").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Acme Corp"))
                .andExpect(jsonPath("$.targetAudience").value("millennials"))
                .andExpect(jsonPath("$.campaignPerformance.overallRoas").value(4))
                .andExpect(jsonPath("$.creativeHealth.winners").value(5));
    }

    @Test void strategyEmpty() throws Exception {
        when(dashboardService.getStrategy(eq(BIZ))).thenReturn(
                new StrategyResponse(BIZ, null, null, null,
                        SpendSummary.EMPTY, CreativeHealth.EMPTY, List.of()));

        mvc.perform(get("/analytics/dashboard/strategy").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").isEmpty())
                .andExpect(jsonPath("$.campaignPerformance.totalSpend").value(0));
    }

    // ─── Platforms ──────────────────────────────────────────────────────

    @Test void platformsOk() throws Exception {
        UUID connId = UUID.randomUUID();
        when(dashboardService.getPlatforms(eq(BIZ), eq(30))).thenReturn(
                new PlatformsResponse(BIZ, 30, List.of(
                        new PlatformCard("META", connId, "My Ad Account", "ACTIVE", Instant.now(),
                                bd(1000), 50000, 2000, 100, bd(5000), 45000, 12,
                                List.of(new PlatformCampaign("camp1", "Summer Sale", bd(500), 25000, 1000, 50)),
                                List.of(new PlatformAd("ad1", "Hero Image", bd(200), 10000, 400, 20, 5.0))))));

        mvc.perform(get("/analytics/dashboard/platforms").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.platforms[0].platform").value("META"))
                .andExpect(jsonPath("$.platforms[0].mappedAssets").value(12))
                .andExpect(jsonPath("$.platforms[0].topCampaigns[0].campaignName").value("Summer Sale"))
                .andExpect(jsonPath("$.platforms[0].topAds[0].avgRoas").value(5.0));
    }

    @Test void platformsEmpty() throws Exception {
        when(dashboardService.getPlatforms(eq(BIZ), eq(30))).thenReturn(
                new PlatformsResponse(BIZ, 30, List.of()));

        mvc.perform(get("/analytics/dashboard/platforms").param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms").isEmpty());
    }

    @Test void platformsCustomDays() throws Exception {
        when(dashboardService.getPlatforms(eq(BIZ), eq(90))).thenReturn(
                new PlatformsResponse(BIZ, 90, List.of()));

        mvc.perform(get("/analytics/dashboard/platforms")
                        .param("businessId", BIZ.toString())
                        .param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(90));
    }

    private static BigDecimal bd(double v) { return BigDecimal.valueOf(v); }
}
