package com.marketing.analytics.api;

import com.marketing.analytics.service.ExecutionService;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/execution")
@Validated
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);
    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    // ─── Plan Endpoints ─────────────────────────────────────────────────────

    @PostMapping("/plans")
    public Map<String, Object> createPlan(
            @RequestParam @NotNull UUID businessId,
            @RequestBody Map<String, Object> request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/plans businessId={}", businessId);
        return executionService.createPlanFromStrategy(businessId, request);
    }

    @PostMapping("/plans/manual")
    public Map<String, Object> createManualPlan(
            @RequestParam @NotNull UUID businessId,
            @RequestBody Map<String, Object> request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/plans/manual businessId={}", businessId);
        return executionService.createManualPlan(businessId, request);
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> listPlans(
            @RequestParam @NotNull UUID businessId,
            @RequestParam(required = false) String status) {
        log.info("GET /analytics/execution/plans businessId={} status={}", businessId, status);
        return executionService.listPlans(businessId, status);
    }

    @GetMapping("/plans/{planId}")
    public Map<String, Object> getPlan(@PathVariable UUID planId) {
        log.info("GET /analytics/execution/plans/{}", planId);
        return executionService.getPlan(planId);
    }

    @PostMapping("/plans/{planId}/activate")
    public Map<String, Object> activatePlan(@PathVariable UUID planId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/plans/{}/activate", planId);
        return executionService.activatePlan(planId);
    }

    @PostMapping("/plans/{planId}/cancel")
    public Map<String, Object> cancelPlan(@PathVariable UUID planId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/plans/{}/cancel", planId);
        return executionService.cancelPlan(planId);
    }

    @GetMapping("/plans/{planId}/ready-tasks")
    public List<Map<String, Object>> readyTasks(@PathVariable UUID planId) {
        log.info("GET /analytics/execution/plans/{}/ready-tasks", planId);
        return executionService.getReadyTasks(planId);
    }

    // ─── Task Endpoints ─────────────────────────────────────────────────────

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable UUID taskId) {
        log.info("GET /analytics/execution/tasks/{}", taskId);
        return executionService.getTaskDetail(taskId);
    }

    @PostMapping("/tasks/{taskId}/start")
    public Map<String, Object> startTask(@PathVariable UUID taskId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/tasks/{}/start", taskId);
        return executionService.startTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Map<String, Object> completeTask(
            @PathVariable UUID taskId,
            @RequestBody(required = false) Map<String, Object> output) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/tasks/{}/complete", taskId);
        return executionService.completeTask(taskId, output != null ? output : Map.of());
    }

    @PostMapping("/tasks/{taskId}/fail")
    public Map<String, Object> failTask(
            @PathVariable UUID taskId,
            @RequestBody Map<String, Object> body) {
        MDC.put("requestId", UUID.randomUUID().toString());
        String errorMessage = (String) body.getOrDefault("errorMessage", "Task failed");
        log.info("POST /analytics/execution/tasks/{}/fail", taskId);
        return executionService.failTask(taskId, errorMessage);
    }

    @PostMapping("/tasks/{taskId}/skip")
    public Map<String, Object> skipTask(@PathVariable UUID taskId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/tasks/{}/skip", taskId);
        return executionService.skipTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Map<String, Object> retryTask(@PathVariable UUID taskId) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/execution/tasks/{}/retry", taskId);
        return executionService.retryTask(taskId);
    }
}
