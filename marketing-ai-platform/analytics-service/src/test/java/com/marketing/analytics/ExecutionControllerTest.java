package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.api.ExecutionController;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExecutionController.class)
@Import(GlobalExceptionHandler.class)
class ExecutionControllerTest {

    @Autowired MockMvc mvc;

    @MockBean ExecutionService executionService;
    @MockBean SnapshotService snapshotService;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository perfRepo;
    @MockBean CreativeOptimizationRecommendationRepository recRepo;
    @MockBean AdPlatformIntegrationService integrationService;
    @MockBean CreativeWinnerScoringService scoringService;
    @MockBean CreativeOptimizationRecommendationService recommendationService;
    @MockBean RecommendationActionService actionService;
    @MockBean DashboardAggregationService dashboardAggregationService;

    private static final UUID BIZ_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    // ─── Plan Endpoints ─────────────────────────────────────────────────

    @Test void createPlanOk() throws Exception {
        when(executionService.createPlanFromStrategy(eq(BIZ_ID), any()))
                .thenReturn(Map.of("id", PLAN_ID.toString(), "status", "DRAFT", "totalTasks", 3));

        mvc.perform(post("/analytics/execution/plans")
                .param("businessId", BIZ_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Plan\",\"tasks\":[{\"name\":\"T1\",\"taskType\":\"SETUP\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test void createManualPlanOk() throws Exception {
        when(executionService.createManualPlan(eq(BIZ_ID), any()))
                .thenReturn(Map.of("id", PLAN_ID.toString(), "status", "DRAFT", "totalTasks", 1));

        mvc.perform(post("/analytics/execution/plans/manual")
                .param("businessId", BIZ_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tasks\":[{\"name\":\"T1\",\"taskType\":\"SETUP\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1));
    }

    @Test void listPlansOk() throws Exception {
        when(executionService.listPlans(BIZ_ID, null))
                .thenReturn(List.of(Map.of("id", PLAN_ID.toString(), "status", "ACTIVE")));

        mvc.perform(get("/analytics/execution/plans")
                .param("businessId", BIZ_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test void getPlanOk() throws Exception {
        when(executionService.getPlan(PLAN_ID))
                .thenReturn(Map.of("id", PLAN_ID.toString(), "status", "ACTIVE", "tasks", List.of()));

        mvc.perform(get("/analytics/execution/plans/" + PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test void activatePlanOk() throws Exception {
        when(executionService.activatePlan(PLAN_ID))
                .thenReturn(Map.of("id", PLAN_ID.toString(), "status", "ACTIVE"));

        mvc.perform(post("/analytics/execution/plans/" + PLAN_ID + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test void cancelPlanOk() throws Exception {
        when(executionService.cancelPlan(PLAN_ID))
                .thenReturn(Map.of("id", PLAN_ID.toString(), "status", "CANCELLED"));

        mvc.perform(post("/analytics/execution/plans/" + PLAN_ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test void readyTasksOk() throws Exception {
        when(executionService.getReadyTasks(PLAN_ID))
                .thenReturn(List.of(Map.of("id", TASK_ID.toString(), "status", "PENDING")));

        mvc.perform(get("/analytics/execution/plans/" + PLAN_ID + "/ready-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ─── Task Endpoints ─────────────────────────────────────────────────

    @Test void getTaskOk() throws Exception {
        when(executionService.getTaskDetail(TASK_ID))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "PENDING", "runs", List.of()));

        mvc.perform(get("/analytics/execution/tasks/" + TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test void startTaskOk() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionService.startTask(TASK_ID))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "IN_PROGRESS", "runId", runId.toString()));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.runId").exists());
    }

    @Test void completeTaskOk() throws Exception {
        when(executionService.completeTask(eq(TASK_ID), any()))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "COMPLETED"));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"output\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test void failTaskOk() throws Exception {
        when(executionService.failTask(eq(TASK_ID), any()))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "PENDING"));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorMessage\":\"timeout\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test void skipTaskOk() throws Exception {
        when(executionService.skipTask(TASK_ID))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "SKIPPED"));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test void retryTaskOk() throws Exception {
        when(executionService.retryTask(TASK_ID))
                .thenReturn(Map.of("id", TASK_ID.toString(), "status", "PENDING"));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ─── Error Handling ─────────────────────────────────────────────────

    @Test void startTaskNotFoundReturns400() throws Exception {
        when(executionService.startTask(TASK_ID))
                .thenThrow(new IllegalArgumentException("Task not found: " + TASK_ID));

        mvc.perform(post("/analytics/execution/tasks/" + TASK_ID + "/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test void createPlanMissingBusinessIdReturns400OrServerError() throws Exception {
        mvc.perform(post("/analytics/execution/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\"}"))
                .andExpect(status().is5xxServerError());
    }
}
