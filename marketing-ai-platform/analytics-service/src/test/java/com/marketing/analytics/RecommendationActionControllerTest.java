package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.AdPlatformIntegrationService;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.RecommendationActionService;
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
class RecommendationActionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository repo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;
    @MockBean com.marketing.analytics.service.DashboardAggregationService dashboardAggregationService;

    // --- Apply endpoint ---

    @Test void applyOk() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.apply(recId)).thenReturn(Map.of(
                "recommendationId", recId.toString(),
                "status", "APPLIED",
                "title", "Scale top performer"
        ));
        mvc.perform(post("/analytics/recommendations/" + recId + "/apply"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.title").value("Scale top performer"));
    }

    @Test void applyNotFound() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.apply(recId)).thenThrow(new IllegalArgumentException("Recommendation not found: " + recId));
        mvc.perform(post("/analytics/recommendations/" + recId + "/apply"))
            .andExpect(status().isBadRequest());
    }

    @Test void applyAlreadyDismissed() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.apply(recId)).thenThrow(new IllegalArgumentException("Cannot apply a dismissed recommendation: " + recId));
        mvc.perform(post("/analytics/recommendations/" + recId + "/apply"))
            .andExpect(status().isBadRequest());
    }

    // --- Dismiss endpoint ---

    @Test void dismissOk() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.dismiss(recId)).thenReturn(Map.of(
                "recommendationId", recId.toString(),
                "status", "DISMISSED"
        ));
        mvc.perform(post("/analytics/recommendations/" + recId + "/dismiss"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test void dismissNotFound() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.dismiss(recId)).thenThrow(new IllegalArgumentException("Recommendation not found: " + recId));
        mvc.perform(post("/analytics/recommendations/" + recId + "/dismiss"))
            .andExpect(status().isBadRequest());
    }

    @Test void dismissAlreadyApplied() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.dismiss(recId)).thenThrow(new IllegalArgumentException("Cannot dismiss an applied recommendation: " + recId));
        mvc.perform(post("/analytics/recommendations/" + recId + "/dismiss"))
            .andExpect(status().isBadRequest());
    }

    // --- Detail endpoint ---

    @Test void detailOk() throws Exception {
        UUID recId = UUID.randomUUID();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("recommendationId", recId.toString());
        detail.put("title", "Scale asset abc");
        detail.put("status", "OPEN");
        detail.put("availableActions", List.of("APPLY", "DISMISS", "GENERATE_VARIANTS", "EXPORT_PACKAGE"));
        when(actionService.getDetail(recId)).thenReturn(detail);

        mvc.perform(get("/analytics/recommendations/" + recId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Scale asset abc"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.availableActions").isArray());
    }

    @Test void detailNotFound() throws Exception {
        UUID recId = UUID.randomUUID();
        when(actionService.getDetail(recId)).thenThrow(new IllegalArgumentException("Recommendation not found: " + recId));
        mvc.perform(get("/analytics/recommendations/" + recId))
            .andExpect(status().isBadRequest());
    }

    // --- List endpoint ---

    @Test void listEmpty() throws Exception {
        UUID businessId = UUID.randomUUID();
        when(actionService.list(eq(businessId), any(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/analytics/recommendations")
                .param("businessId", businessId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessId").value(businessId.toString()))
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test void listWithStatusFilter() throws Exception {
        UUID businessId = UUID.randomUUID();
        when(actionService.list(eq(businessId), eq("OPEN"), eq(30))).thenReturn(List.of(
                Map.of("title", "Scale something", "status", "OPEN")
        ));
        mvc.perform(get("/analytics/recommendations")
                .param("businessId", businessId.toString())
                .param("status", "OPEN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.recommendations[0].title").value("Scale something"));
    }

    @Test void listWithCustomDays() throws Exception {
        UUID businessId = UUID.randomUUID();
        when(actionService.list(eq(businessId), any(), eq(7))).thenReturn(List.of());
        mvc.perform(get("/analytics/recommendations")
                .param("businessId", businessId.toString())
                .param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(7));
    }

    // --- Dashboard endpoint ---

    @Test void dashboardOk() throws Exception {
        UUID businessId = UUID.randomUUID();
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("businessId", businessId);
        dashboard.put("totalRecommendations", 3);
        dashboard.put("highPriority", List.of(Map.of("title", "Scale winner", "priority", "HIGH")));
        dashboard.put("mediumPriority", List.of(Map.of("title", "Test more", "priority", "MEDIUM")));
        dashboard.put("lowPriority", List.of(Map.of("title", "Consider adapting", "priority", "LOW")));
        when(actionService.dashboard(businessId)).thenReturn(dashboard);

        mvc.perform(get("/analytics/recommendations/dashboard")
                .param("businessId", businessId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRecommendations").value(3))
            .andExpect(jsonPath("$.highPriority").isArray())
            .andExpect(jsonPath("$.highPriority[0].title").value("Scale winner"))
            .andExpect(jsonPath("$.mediumPriority").isArray())
            .andExpect(jsonPath("$.lowPriority").isArray());
    }

    @Test void dashboardEmpty() throws Exception {
        UUID businessId = UUID.randomUUID();
        when(actionService.dashboard(businessId)).thenReturn(Map.of(
                "businessId", businessId,
                "totalRecommendations", 0,
                "highPriority", List.of(),
                "mediumPriority", List.of(),
                "lowPriority", List.of()
        ));
        mvc.perform(get("/analytics/recommendations/dashboard")
                .param("businessId", businessId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRecommendations").value(0));
    }

    // --- Export launch package endpoint ---

    @Test void exportLaunchPackageOk() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("businessId", businessId.toString());
        pkg.put("recommendationId", recId.toString());
        pkg.put("campaignName", "scale-" + assetId.toString().substring(0, 8) + "-2025-01-01");
        pkg.put("platform", "meta");
        pkg.put("objective", "conversions");
        pkg.put("budgetGuidance", "Increase daily budget by 20-30%");
        pkg.put("targetingGuidance", "Maintain current targeting.");
        pkg.put("copy", Map.of("headline", "Test headline", "primaryText", "Test text", "cta", "Shop Now"));
        pkg.put("assetLinks", List.of(Map.of("assetId", assetId.toString())));
        pkg.put("landingPageGuidance", "Ensure landing page matches ad promise");
        pkg.put("trackingChecklist", List.of("Install pixel", "Setup conversion events"));
        pkg.put("notes", List.of("Generated from SCALE recommendation"));

        when(actionService.exportLaunchPackage(businessId, recId)).thenReturn(pkg);

        mvc.perform(get("/analytics/recommendations/" + recId + "/export-launch-package")
                .param("businessId", businessId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.campaignName").exists())
            .andExpect(jsonPath("$.platform").value("meta"))
            .andExpect(jsonPath("$.objective").value("conversions"))
            .andExpect(jsonPath("$.budgetGuidance").exists())
            .andExpect(jsonPath("$.copy.headline").value("Test headline"))
            .andExpect(jsonPath("$.assetLinks").isArray())
            .andExpect(jsonPath("$.trackingChecklist").isArray())
            .andExpect(jsonPath("$.notes").isArray());
    }

    @Test void exportLaunchPackageNotFound() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        when(actionService.exportLaunchPackage(businessId, recId))
                .thenThrow(new IllegalArgumentException("Recommendation not found: " + recId));
        mvc.perform(get("/analytics/recommendations/" + recId + "/export-launch-package")
                .param("businessId", businessId.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test void exportLaunchPackageWrongBusiness() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        when(actionService.exportLaunchPackage(businessId, recId))
                .thenThrow(new IllegalArgumentException("Recommendation does not belong to business: " + businessId));
        mvc.perform(get("/analytics/recommendations/" + recId + "/export-launch-package")
                .param("businessId", businessId.toString()))
            .andExpect(status().isBadRequest());
    }
}
