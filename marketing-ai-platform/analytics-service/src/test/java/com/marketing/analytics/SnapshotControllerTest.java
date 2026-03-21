package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.api.SnapshotController;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.*;
import com.marketing.common.GlobalExceptionHandler;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SnapshotController.class)
@Import(GlobalExceptionHandler.class)
class SnapshotControllerTest {

    @Autowired MockMvc mvc;

    @MockBean SnapshotService snapshotService;
    @MockBean ExecutionService executionService;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository perfRepo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;
    @MockBean DashboardAggregationService dashboardAggregationService;

    static final UUID BIZ = UUID.randomUUID();
    static final UUID SNAP_ID = UUID.randomUUID();
    static final UUID PLAN_ID = UUID.randomUUID();

    @Test void captureOk() throws Exception {
        when(snapshotService.captureSnapshot(eq(BIZ), any()))
                .thenReturn(Map.of("id", SNAP_ID.toString(), "snapshotType", "MANUAL"));

        mvc.perform(post("/analytics/snapshots")
                .param("businessId", BIZ.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"snapshotType\":\"MANUAL\",\"label\":\"Week 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotType").value("MANUAL"));
    }

    @Test void captureMissingBusinessIdReturnsError() throws Exception {
        mvc.perform(post("/analytics/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"snapshotType\":\"MANUAL\"}"))
                .andExpect(status().is5xxServerError());
    }

    @Test void listOk() throws Exception {
        when(snapshotService.listSnapshots(eq(BIZ), isNull(), eq(90)))
                .thenReturn(List.of(Map.of("id", SNAP_ID.toString(), "snapshotType", "MANUAL")));

        mvc.perform(get("/analytics/snapshots")
                .param("businessId", BIZ.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].snapshotType").value("MANUAL"));
    }

    @Test void listWithTypeFilter() throws Exception {
        when(snapshotService.listSnapshots(BIZ, "PRE_PLAN", 90))
                .thenReturn(List.of());

        mvc.perform(get("/analytics/snapshots")
                .param("businessId", BIZ.toString())
                .param("snapshotType", "PRE_PLAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test void getSnapshotOk() throws Exception {
        when(snapshotService.getSnapshot(SNAP_ID))
                .thenReturn(Map.of("id", SNAP_ID.toString(), "snapshotType", "MANUAL"));

        mvc.perform(get("/analytics/snapshots/" + SNAP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SNAP_ID.toString()));
    }

    @Test void getSnapshotNotFoundReturns400() throws Exception {
        when(snapshotService.getSnapshot(SNAP_ID))
                .thenThrow(new IllegalArgumentException("Snapshot not found: " + SNAP_ID));

        mvc.perform(get("/analytics/snapshots/" + SNAP_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test void planSnapshotsOk() throws Exception {
        when(snapshotService.listPlanSnapshots(PLAN_ID))
                .thenReturn(List.of(Map.of("id", SNAP_ID.toString(), "planId", PLAN_ID.toString())));

        mvc.perform(get("/analytics/snapshots/plan/" + PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planId").value(PLAN_ID.toString()));
    }

    @Test void compareOk() throws Exception {
        UUID baseId = UUID.randomUUID();
        UUID currId = UUID.randomUUID();
        when(snapshotService.compareSnapshots(baseId, currId))
                .thenReturn(Map.of("baselineId", baseId.toString(), "currentId", currId.toString(),
                        "deltas", Map.of()));

        mvc.perform(get("/analytics/snapshots/compare")
                .param("baselineId", baseId.toString())
                .param("currentId", currId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineId").value(baseId.toString()))
                .andExpect(jsonPath("$.deltas").exists());
    }

    @Test void compareBaselineNotFoundReturns400() throws Exception {
        UUID baseId = UUID.randomUUID();
        UUID currId = UUID.randomUUID();
        when(snapshotService.compareSnapshots(baseId, currId))
                .thenThrow(new IllegalArgumentException("Baseline snapshot not found: " + baseId));

        mvc.perform(get("/analytics/snapshots/compare")
                .param("baselineId", baseId.toString())
                .param("currentId", currId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
