package com.marketing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.ExecutionPlan;
import com.marketing.analytics.model.ExecutionTask;
import com.marketing.analytics.model.ExecutionTaskRun;
import com.marketing.analytics.repo.ExecutionPlanRepository;
import com.marketing.analytics.repo.ExecutionTaskRepository;
import com.marketing.analytics.repo.ExecutionTaskRunRepository;

import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages execution plan lifecycle: creation from strategy, task status transitions,
 * retry semantics, dependency resolution, and plan progress tracking.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private final ExecutionPlanRepository planRepo;
    private final ExecutionTaskRepository taskRepo;
    private final ExecutionTaskRunRepository runRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${execution.max-active-plans:5}")
    private int maxActivePlans;

    @Value("${execution.default-max-retries:3}")
    private int defaultMaxRetries;

    // Valid status constants
    private static final String PLAN_DRAFT = "DRAFT";
    private static final String PLAN_ACTIVE = "ACTIVE";
    private static final String PLAN_IN_PROGRESS = "IN_PROGRESS";
    private static final String PLAN_COMPLETED = "COMPLETED";
    private static final String PLAN_FAILED = "FAILED";
    private static final String PLAN_CANCELLED = "CANCELLED";

    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_IN_PROGRESS = "IN_PROGRESS";
    private static final String TASK_COMPLETED = "COMPLETED";
    private static final String TASK_FAILED = "FAILED";
    private static final String TASK_SKIPPED = "SKIPPED";
    private static final String TASK_BLOCKED = "BLOCKED";

    private static final String RUN_RUNNING = "RUNNING";
    private static final String RUN_SUCCEEDED = "SUCCEEDED";
    private static final String RUN_FAILED = "FAILED";

    private static final Set<String> VALID_TASK_TYPES = Set.of(
            "SETUP", "CREATE_CREATIVE", "LAUNCH_CAMPAIGN",
            "REVIEW", "GENERATE_LANDING_PAGE", "GENERATE_OFFER", "CUSTOM");

    public ExecutionService(ExecutionPlanRepository planRepo,
                            ExecutionTaskRepository taskRepo,
                            ExecutionTaskRunRepository runRepo) {
        this.planRepo = planRepo;
        this.taskRepo = taskRepo;
        this.runRepo = runRepo;
    }

    // ─── Plan Creation ──────────────────────────────────────────────────────

    /**
     * Create an execution plan from a strategy response.
     * Idempotent: if a plan already exists for this business + strategy run, returns it.
     */
    @Transactional
    public Map<String, Object> createPlanFromStrategy(UUID businessId, Map<String, Object> request) {
        String requestId = MDC.get("requestId");
        UUID strategyRunId = parseUuid(request.get("strategyRunId"));
        String name = (String) request.getOrDefault("name", "Strategy Execution Plan");
        String description = (String) request.get("description");

        // Idempotency: check for existing plan with same strategy run
        if (strategyRunId != null) {
            Optional<ExecutionPlan> existing = planRepo.findByBusinessIdAndStrategyRunId(businessId, strategyRunId);
            if (existing.isPresent()) {
                log.info("Plan already exists for business={} strategyRun={}, returning existing plan requestId={}",
                        businessId, strategyRunId, requestId);
                return planToMap(existing.get());
            }
        }

        // Guard: limit concurrent active plans
        long activePlans = planRepo.countActivePlans(businessId);
        if (activePlans >= maxActivePlans) {
            throw new IllegalArgumentException(
                    "Business " + businessId + " already has " + activePlans + " active plans (max " + maxActivePlans + ")");
        }

        // Extract strategy sections for task generation
        @SuppressWarnings("unchecked")
        Map<String, Object> strategy = (Map<String, Object>) request.getOrDefault("strategy", Map.of());
        String executionRoadmap = (String) strategy.get("executionRoadmap");
        String setupChecklist = (String) strategy.get("setupChecklist");
        String creativesNeeded = (String) strategy.get("creativesNeeded");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> manualTasks = (List<Map<String, Object>>) request.get("tasks");

        Instant now = Instant.now();
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId(UUID.randomUUID());
        plan.setBusinessId(businessId);
        plan.setStrategyRunId(strategyRunId);
        plan.setName(name);
        plan.setDescription(description);
        plan.setSourceType(strategyRunId != null ? "STRATEGY" : "MANUAL");
        plan.setSourceJson(toJsonSafe(strategy));
        plan.setStatus(PLAN_DRAFT);
        plan.setTotalTasks(0);
        plan.setCompletedTasks(0);
        plan.setFailedTasks(0);
        plan.setSkippedTasks(0);
        plan.setVersion(1);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planRepo.save(plan);

        // Generate tasks from strategy fields or manual task list
        List<ExecutionTask> tasks;
        if (manualTasks != null && !manualTasks.isEmpty()) {
            tasks = buildManualTasks(plan, businessId, manualTasks, now);
        } else {
            tasks = buildTasksFromStrategy(plan, businessId, executionRoadmap, setupChecklist, creativesNeeded, now);
        }

        plan.setTotalTasks(tasks.size());
        plan.setUpdatedAt(Instant.now());
        planRepo.save(plan);

        log.info("Created execution plan {} with {} tasks for business={} requestId={}",
                plan.getId(), tasks.size(), businessId, requestId);

        Map<String, Object> result = planToMap(plan);
        result.put("tasks", tasks.stream().map(this::taskToMap).toList());
        return result;
    }

    /**
     * Create a minimal plan with manually specified tasks.
     */
    @Transactional
    public Map<String, Object> createManualPlan(UUID businessId, Map<String, Object> request) {
        String requestId = MDC.get("requestId");
        String name = (String) request.getOrDefault("name", "Manual Execution Plan");
        String description = (String) request.get("description");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> taskDefs = (List<Map<String, Object>>) request.get("tasks");
        if (taskDefs == null || taskDefs.isEmpty()) {
            throw new IllegalArgumentException("Manual plan requires at least one task");
        }

        // Guard: limit concurrent active plans
        long activePlans = planRepo.countActivePlans(businessId);
        if (activePlans >= maxActivePlans) {
            throw new IllegalArgumentException(
                    "Business " + businessId + " already has " + activePlans + " active plans (max " + maxActivePlans + ")");
        }

        Instant now = Instant.now();
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId(UUID.randomUUID());
        plan.setBusinessId(businessId);
        plan.setName(name);
        plan.setDescription(description);
        plan.setSourceType("MANUAL");
        plan.setStatus(PLAN_DRAFT);
        plan.setTotalTasks(0);
        plan.setCompletedTasks(0);
        plan.setFailedTasks(0);
        plan.setSkippedTasks(0);
        plan.setVersion(1);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planRepo.save(plan);

        List<ExecutionTask> tasks = buildManualTasks(plan, businessId, taskDefs, now);

        plan.setTotalTasks(tasks.size());
        plan.setUpdatedAt(Instant.now());
        planRepo.save(plan);

        log.info("Created manual plan {} with {} tasks for business={} requestId={}",
                plan.getId(), tasks.size(), businessId, requestId);

        Map<String, Object> result = planToMap(plan);
        result.put("tasks", tasks.stream().map(this::taskToMap).toList());
        return result;
    }

    // ─── Plan Lifecycle ─────────────────────────────────────────────────────

    /**
     * Activate a DRAFT plan, making it ready for task execution.
     */
    @Transactional
    public Map<String, Object> activatePlan(UUID planId) {
        String requestId = MDC.get("requestId");
        ExecutionPlan plan = findPlanOrThrow(planId);

        if (PLAN_ACTIVE.equals(plan.getStatus())) {
            log.info("Plan {} already ACTIVE, idempotent return requestId={}", planId, requestId);
            return planToMap(plan);
        }
        if (!PLAN_DRAFT.equals(plan.getStatus())) {
            throw new IllegalArgumentException("Cannot activate plan in status " + plan.getStatus() + ": " + planId);
        }
        if (plan.getTotalTasks() == 0) {
            throw new IllegalArgumentException("Cannot activate plan with no tasks: " + planId);
        }

        plan.setStatus(PLAN_ACTIVE);
        plan.setUpdatedAt(Instant.now());
        plan.setVersion(plan.getVersion() + 1);
        planRepo.save(plan);
        log.info("Plan {} activated requestId={}", planId, requestId);
        return planToMap(plan);
    }

    /**
     * Cancel a plan. Tasks in PENDING/BLOCKED are marked SKIPPED.
     * Idempotent — already cancelled plans return current state.
     */
    @Transactional
    public Map<String, Object> cancelPlan(UUID planId) {
        String requestId = MDC.get("requestId");
        ExecutionPlan plan = findPlanOrThrow(planId);

        if (PLAN_CANCELLED.equals(plan.getStatus())) {
            log.info("Plan {} already CANCELLED, idempotent return requestId={}", planId, requestId);
            return planToMap(plan);
        }
        if (PLAN_COMPLETED.equals(plan.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel a completed plan: " + planId);
        }

        // Skip all non-terminal tasks
        List<ExecutionTask> pendingTasks = taskRepo.findByPlanIdAndStatusOrderBySequenceOrder(planId, TASK_PENDING);
        List<ExecutionTask> blockedTasks = taskRepo.findByPlanIdAndStatusOrderBySequenceOrder(planId, TASK_BLOCKED);
        Instant now = Instant.now();
        int skipped = 0;
        for (ExecutionTask t : pendingTasks) {
            t.setStatus(TASK_SKIPPED);
            t.setUpdatedAt(now);
            taskRepo.save(t);
            skipped++;
        }
        for (ExecutionTask t : blockedTasks) {
            t.setStatus(TASK_SKIPPED);
            t.setUpdatedAt(now);
            taskRepo.save(t);
            skipped++;
        }

        plan.setStatus(PLAN_CANCELLED);
        plan.setSkippedTasks(plan.getSkippedTasks() + skipped);
        plan.setUpdatedAt(now);
        plan.setVersion(plan.getVersion() + 1);
        planRepo.save(plan);
        log.info("Plan {} cancelled, skipped {} tasks requestId={}", planId, skipped, requestId);
        return planToMap(plan);
    }

    /**
     * Get plan details including all tasks.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlan(UUID planId) {
        ExecutionPlan plan = findPlanOrThrow(planId);
        List<ExecutionTask> tasks = taskRepo.findByPlanIdOrderBySequenceOrder(planId);
        Map<String, Object> result = planToMap(plan);
        result.put("tasks", tasks.stream().map(this::taskToMap).toList());
        return result;
    }

    /**
     * List all plans for a business, optionally filtered by status.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlans(UUID businessId, String status) {
        List<ExecutionPlan> plans;
        if (status != null && !status.isBlank()) {
            plans = planRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, status);
        } else {
            plans = planRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        }
        return plans.stream().map(this::planToMap).toList();
    }

    /**
     * Get tasks ready for execution (dependencies satisfied, status PENDING).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReadyTasks(UUID planId) {
        findPlanOrThrow(planId);
        List<ExecutionTask> readyTasks = taskRepo.findReadyTasks(planId);
        return readyTasks.stream().map(this::taskToMap).toList();
    }

    // ─── Task Lifecycle ─────────────────────────────────────────────────────

    /**
     * Start executing a task. Creates a task run.
     * Validates: plan is ACTIVE/IN_PROGRESS, task is PENDING, dependencies are met.
     */
    @Transactional
    public Map<String, Object> startTask(UUID taskId) {
        String requestId = MDC.get("requestId");
        ExecutionTask task = findTaskOrThrow(taskId);

        // Idempotent: already running returns current state
        if (TASK_IN_PROGRESS.equals(task.getStatus())) {
            log.info("Task {} already IN_PROGRESS, idempotent return requestId={}", taskId, requestId);
            return taskToMap(task);
        }
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("Cannot start task in status " + task.getStatus() + ": " + taskId);
        }

        // Plan must be ACTIVE or IN_PROGRESS
        ExecutionPlan plan = findPlanOrThrow(task.getPlanId());
        if (!PLAN_ACTIVE.equals(plan.getStatus()) && !PLAN_IN_PROGRESS.equals(plan.getStatus())) {
            throw new IllegalArgumentException("Cannot start task — plan is " + plan.getStatus() + ": " + plan.getId());
        }

        // Dependency check
        if (task.getDependsOnTaskId() != null) {
            ExecutionTask dep = findTaskOrThrow(task.getDependsOnTaskId());
            if (!TASK_COMPLETED.equals(dep.getStatus()) && !TASK_SKIPPED.equals(dep.getStatus())) {
                throw new IllegalArgumentException(
                        "Cannot start task " + taskId + " — dependency " + dep.getId() + " is " + dep.getStatus());
            }
        }

        Instant now = Instant.now();
        task.setStatus(TASK_IN_PROGRESS);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        task.setVersion(task.getVersion() + 1);
        taskRepo.save(task);

        // Create a task run record
        ExecutionTaskRun run = new ExecutionTaskRun();
        run.setId(UUID.randomUUID());
        run.setTaskId(taskId);
        run.setAttempt(task.getRetryCount() + 1);
        run.setStatus(RUN_RUNNING);
        run.setInputJson(task.getInputJson());
        run.setStartedAt(now);
        runRepo.save(run);

        // Transition plan to IN_PROGRESS if currently ACTIVE
        if (PLAN_ACTIVE.equals(plan.getStatus())) {
            plan.setStatus(PLAN_IN_PROGRESS);
            plan.setStartedAt(now);
            plan.setUpdatedAt(now);
            plan.setVersion(plan.getVersion() + 1);
            planRepo.save(plan);
        }

        log.info("Task {} started, run {} created requestId={}", taskId, run.getId(), requestId);
        Map<String, Object> result = taskToMap(task);
        result.put("runId", run.getId());
        return result;
    }

    /**
     * Mark a task as completed with optional output.
     * Updates plan progress counters. If all tasks done, completes the plan.
     */
    @Transactional
    public Map<String, Object> completeTask(UUID taskId, Map<String, Object> output) {
        String requestId = MDC.get("requestId");
        ExecutionTask task = findTaskOrThrow(taskId);

        // Idempotent
        if (TASK_COMPLETED.equals(task.getStatus())) {
            log.info("Task {} already COMPLETED, idempotent return requestId={}", taskId, requestId);
            return taskToMap(task);
        }
        if (!TASK_IN_PROGRESS.equals(task.getStatus())) {
            throw new IllegalArgumentException("Cannot complete task in status " + task.getStatus() + ": " + taskId);
        }

        Instant now = Instant.now();
        task.setStatus(TASK_COMPLETED);
        task.setOutputJson(toJsonSafe(output));
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        task.setErrorMessage(null);
        task.setVersion(task.getVersion() + 1);
        taskRepo.save(task);

        // Complete any running task run
        completeActiveRunForTask(taskId, RUN_SUCCEEDED, toJsonSafe(output), null, now);

        // Update plan counters and check for plan completion
        updatePlanProgress(task.getPlanId());

        log.info("Task {} completed requestId={}", taskId, requestId);
        return taskToMap(task);
    }

    /**
     * Mark a task as failed. If retries remain, task goes back to PENDING.
     */
    @Transactional
    public Map<String, Object> failTask(UUID taskId, String errorMessage) {
        String requestId = MDC.get("requestId");
        ExecutionTask task = findTaskOrThrow(taskId);

        if (TASK_FAILED.equals(task.getStatus())) {
            log.info("Task {} already FAILED, idempotent return requestId={}", taskId, requestId);
            return taskToMap(task);
        }
        if (!TASK_IN_PROGRESS.equals(task.getStatus())) {
            throw new IllegalArgumentException("Cannot fail task in status " + task.getStatus() + ": " + taskId);
        }

        Instant now = Instant.now();
        String safeErrorMessage = errorMessage != null ? errorMessage.substring(0, Math.min(errorMessage.length(), 2000)) : "Unknown error";

        // Complete the running task run as failed
        completeActiveRunForTask(taskId, RUN_FAILED, null, safeErrorMessage, now);

        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(safeErrorMessage);
        task.setUpdatedAt(now);
        task.setVersion(task.getVersion() + 1);

        if (task.getRetryCount() < task.getMaxRetries()) {
            // Auto-retry: return to PENDING so it can be re-started
            task.setStatus(TASK_PENDING);
            task.setStartedAt(null);
            taskRepo.save(task);
            log.info("Task {} failed attempt {}/{}, returned to PENDING for retry requestId={}",
                    taskId, task.getRetryCount(), task.getMaxRetries(), requestId);
        } else {
            // Exhausted retries: mark as permanently failed
            task.setStatus(TASK_FAILED);
            task.setCompletedAt(now);
            taskRepo.save(task);

            // Block dependent tasks
            blockDependentTasks(taskId, now);

            // Update plan counters
            updatePlanProgress(task.getPlanId());
            log.info("Task {} permanently FAILED after {} retries requestId={}",
                    taskId, task.getMaxRetries(), requestId);
        }

        return taskToMap(task);
    }

    /**
     * Skip a task. Marks it as SKIPPED and updates plan counters.
     */
    @Transactional
    public Map<String, Object> skipTask(UUID taskId) {
        String requestId = MDC.get("requestId");
        ExecutionTask task = findTaskOrThrow(taskId);

        if (TASK_SKIPPED.equals(task.getStatus())) {
            log.info("Task {} already SKIPPED, idempotent return requestId={}", taskId, requestId);
            return taskToMap(task);
        }
        if (TASK_COMPLETED.equals(task.getStatus())) {
            throw new IllegalArgumentException("Cannot skip a completed task: " + taskId);
        }
        if (TASK_IN_PROGRESS.equals(task.getStatus())) {
            throw new IllegalArgumentException("Cannot skip a task that is in progress: " + taskId);
        }

        Instant now = Instant.now();
        task.setStatus(TASK_SKIPPED);
        task.setUpdatedAt(now);
        task.setCompletedAt(now);
        task.setVersion(task.getVersion() + 1);
        taskRepo.save(task);

        updatePlanProgress(task.getPlanId());
        log.info("Task {} skipped requestId={}", taskId, requestId);
        return taskToMap(task);
    }

    /**
     * Retry a failed task: resets to PENDING and decrements retry count is not needed
     * since retryCount tracks attempts. Simply resets to allow re-execution.
     */
    @Transactional
    public Map<String, Object> retryTask(UUID taskId) {
        String requestId = MDC.get("requestId");
        ExecutionTask task = findTaskOrThrow(taskId);

        if (!TASK_FAILED.equals(task.getStatus())) {
            throw new IllegalArgumentException("Can only retry a FAILED task: " + taskId + " is " + task.getStatus());
        }

        Instant now = Instant.now();
        task.setStatus(TASK_PENDING);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setErrorMessage(null);
        task.setRetryCount(0);
        task.setMaxRetries(defaultMaxRetries);
        task.setUpdatedAt(now);
        task.setVersion(task.getVersion() + 1);
        taskRepo.save(task);

        // Un-block dependent tasks
        List<ExecutionTask> dependents = taskRepo.findByDependsOnTaskId(taskId);
        for (ExecutionTask dep : dependents) {
            if (TASK_BLOCKED.equals(dep.getStatus())) {
                dep.setStatus(TASK_PENDING);
                dep.setUpdatedAt(now);
                taskRepo.save(dep);
            }
        }

        // Re-sync plan counters
        updatePlanProgress(task.getPlanId());
        log.info("Task {} retried, reset to PENDING requestId={}", taskId, requestId);
        return taskToMap(task);
    }

    /**
     * Get task details with run history.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTaskDetail(UUID taskId) {
        ExecutionTask task = findTaskOrThrow(taskId);
        List<ExecutionTaskRun> runs = runRepo.findByTaskIdOrderByAttemptDesc(taskId);
        Map<String, Object> result = taskToMap(task);
        result.put("runs", runs.stream().map(this::runToMap).toList());
        return result;
    }

    // ─── Task Generation from Strategy ──────────────────────────────────────

    private List<ExecutionTask> buildTasksFromStrategy(ExecutionPlan plan, UUID businessId,
                                                       String executionRoadmap, String setupChecklist,
                                                       String creativesNeeded, Instant now) {
        List<ExecutionTask> tasks = new ArrayList<>();
        int seq = 0;
        UUID previousTaskId = null;

        // Setup tasks from checklist
        if (setupChecklist != null && !setupChecklist.isBlank()) {
            List<String> items = parseListItems(setupChecklist);
            for (String item : items) {
                ExecutionTask task = createTask(plan, businessId, "SETUP", "Setup: " + truncate(item, 120),
                        item, seq++, previousTaskId, null, now);
                tasks.add(task);
                previousTaskId = task.getId();
            }
        }

        // Creative tasks
        if (creativesNeeded != null && !creativesNeeded.isBlank()) {
            List<String> items = parseListItems(creativesNeeded);
            for (String item : items) {
                ExecutionTask task = createTask(plan, businessId, "CREATE_CREATIVE",
                        "Create: " + truncate(item, 120), item, seq++, previousTaskId, null, now);
                tasks.add(task);
                // Creatives are independent — don't chain them
            }
        }

        // Execution roadmap tasks (week-by-week)
        if (executionRoadmap != null && !executionRoadmap.isBlank()) {
            List<String> weekItems = parseListItems(executionRoadmap);
            for (String item : weekItems) {
                ExecutionTask task = createTask(plan, businessId, "LAUNCH_CAMPAIGN",
                        "Execute: " + truncate(item, 120), item, seq++, null, null, now);
                tasks.add(task);
            }
        }

        // Final review task
        if (!tasks.isEmpty()) {
            ExecutionTask review = createTask(plan, businessId, "REVIEW", "Final Review & Sign-off",
                    "Review all completed tasks and confirm plan execution is complete",
                    seq, null, null, now);
            tasks.add(review);
        }

        return tasks;
    }

    private List<ExecutionTask> buildManualTasks(ExecutionPlan plan, UUID businessId,
                                                  List<Map<String, Object>> taskDefs, Instant now) {
        List<ExecutionTask> tasks = new ArrayList<>();
        int seq = 0;

        for (Map<String, Object> def : taskDefs) {
            String taskType = (String) def.getOrDefault("taskType", "CUSTOM");
            if (!VALID_TASK_TYPES.contains(taskType)) {
                throw new IllegalArgumentException("Invalid task type: " + taskType
                        + ". Valid types: " + VALID_TASK_TYPES);
            }

            String taskName = (String) def.get("name");
            if (taskName == null || taskName.isBlank()) {
                throw new IllegalArgumentException("Task name is required at index " + seq);
            }

            String taskDesc = (String) def.get("description");
            UUID dependsOn = parseUuid(def.get("dependsOnTaskId"));
            UUID recommendationId = parseUuid(def.get("recommendationId"));

            @SuppressWarnings("unchecked")
            Map<String, Object> inputData = (Map<String, Object>) def.get("input");

            int maxRetries = def.containsKey("maxRetries")
                    ? ((Number) def.get("maxRetries")).intValue()
                    : defaultMaxRetries;
            int priority = def.containsKey("priority")
                    ? ((Number) def.get("priority")).intValue()
                    : 0;

            ExecutionTask task = new ExecutionTask();
            task.setId(UUID.randomUUID());
            task.setPlanId(plan.getId());
            task.setBusinessId(businessId);
            task.setRecommendationId(recommendationId);
            task.setDependsOnTaskId(dependsOn);
            task.setTaskType(taskType);
            task.setName(truncate(taskName, 255));
            task.setDescription(taskDesc);
            task.setInputJson(toJsonSafe(inputData));
            task.setStatus(TASK_PENDING);
            task.setPriority(priority);
            task.setSequenceOrder(seq++);
            task.setMaxRetries(maxRetries);
            task.setRetryCount(0);
            task.setVersion(1);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskRepo.save(task);
            tasks.add(task);
        }
        return tasks;
    }

    private ExecutionTask createTask(ExecutionPlan plan, UUID businessId, String taskType,
                                     String name, String description, int seq,
                                     UUID dependsOnTaskId, UUID recommendationId, Instant now) {
        ExecutionTask task = new ExecutionTask();
        task.setId(UUID.randomUUID());
        task.setPlanId(plan.getId());
        task.setBusinessId(businessId);
        task.setRecommendationId(recommendationId);
        task.setDependsOnTaskId(dependsOnTaskId);
        task.setTaskType(taskType);
        task.setName(name);
        task.setDescription(description);
        task.setStatus(TASK_PENDING);
        task.setPriority(0);
        task.setSequenceOrder(seq);
        task.setMaxRetries(defaultMaxRetries);
        task.setRetryCount(0);
        task.setVersion(1);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepo.save(task);
        return task;
    }

    // ─── Plan Progress ──────────────────────────────────────────────────────

    private void updatePlanProgress(UUID planId) {
        ExecutionPlan plan = findPlanOrThrow(planId);
        long total = taskRepo.countByPlanId(planId);
        long completed = taskRepo.countByPlanIdAndStatus(planId, TASK_COMPLETED);
        long failed = taskRepo.countByPlanIdAndStatus(planId, TASK_FAILED);
        long skipped = taskRepo.countByPlanIdAndStatus(planId, TASK_SKIPPED);

        plan.setTotalTasks((int) total);
        plan.setCompletedTasks((int) completed);
        plan.setFailedTasks((int) failed);
        plan.setSkippedTasks((int) skipped);
        plan.setUpdatedAt(Instant.now());

        // Determine if plan is complete
        long terminal = completed + failed + skipped;
        if (terminal == total && total > 0) {
            if (failed > 0) {
                plan.setStatus(PLAN_FAILED);
            } else {
                plan.setStatus(PLAN_COMPLETED);
            }
            plan.setCompletedAt(Instant.now());
            plan.setVersion(plan.getVersion() + 1);
        }

        planRepo.save(plan);
    }

    private void blockDependentTasks(UUID failedTaskId, Instant now) {
        List<ExecutionTask> dependents = taskRepo.findByDependsOnTaskId(failedTaskId);
        for (ExecutionTask dep : dependents) {
            if (TASK_PENDING.equals(dep.getStatus())) {
                dep.setStatus(TASK_BLOCKED);
                dep.setErrorMessage("Blocked: dependency task " + failedTaskId + " failed");
                dep.setUpdatedAt(now);
                taskRepo.save(dep);
                // Recursively block downstream
                blockDependentTasks(dep.getId(), now);
            }
        }
    }

    private void completeActiveRunForTask(UUID taskId, String runStatus, String outputJson, String errorMsg, Instant now) {
        List<ExecutionTaskRun> activeRuns = runRepo.findByTaskIdAndStatus(taskId, RUN_RUNNING);
        for (ExecutionTaskRun run : activeRuns) {
            run.setStatus(runStatus);
            run.setOutputJson(outputJson);
            run.setErrorMessage(errorMsg);
            run.setCompletedAt(now);
            runRepo.save(run);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private ExecutionPlan findPlanOrThrow(UUID planId) {
        return planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Execution plan not found: " + planId));
    }

    private ExecutionTask findTaskOrThrow(UUID taskId) {
        return taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Execution task not found: " + taskId));
    }

    /**
     * Parse text that contains numbered/bulleted list items into separate strings.
     * Handles: "1. Item", "- Item", "• Item", "* Item", newline-separated lines.
     */
    private List<String> parseListItems(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] lines = text.split("\\r?\\n");
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim()
                    .replaceFirst("^\\d+\\.\\s*", "")
                    .replaceFirst("^[-•*]\\s*", "")
                    .trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private UUID parseUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJsonSafe(Object obj) {
        if (obj == null) return null;
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return om.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    Map<String, Object> planToMap(ExecutionPlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("businessId", p.getBusinessId());
        m.put("strategyRunId", p.getStrategyRunId());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("sourceType", p.getSourceType());
        m.put("status", p.getStatus());
        m.put("totalTasks", p.getTotalTasks());
        m.put("completedTasks", p.getCompletedTasks());
        m.put("failedTasks", p.getFailedTasks());
        m.put("skippedTasks", p.getSkippedTasks());
        m.put("progress", p.getTotalTasks() > 0
                ? Math.round((double) (p.getCompletedTasks() + p.getSkippedTasks()) / p.getTotalTasks() * 100)
                : 0);
        m.put("version", p.getVersion());
        m.put("startedAt", p.getStartedAt());
        m.put("completedAt", p.getCompletedAt());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    Map<String, Object> taskToMap(ExecutionTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("planId", t.getPlanId());
        m.put("businessId", t.getBusinessId());
        m.put("recommendationId", t.getRecommendationId());
        m.put("dependsOnTaskId", t.getDependsOnTaskId());
        m.put("taskType", t.getTaskType());
        m.put("name", t.getName());
        m.put("description", t.getDescription());
        m.put("input", parseJsonSafe(t.getInputJson()));
        m.put("output", parseJsonSafe(t.getOutputJson()));
        m.put("status", t.getStatus());
        m.put("priority", t.getPriority());
        m.put("sequenceOrder", t.getSequenceOrder());
        m.put("maxRetries", t.getMaxRetries());
        m.put("retryCount", t.getRetryCount());
        m.put("errorMessage", t.getErrorMessage());
        m.put("version", t.getVersion());
        m.put("startedAt", t.getStartedAt());
        m.put("completedAt", t.getCompletedAt());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private Map<String, Object> runToMap(ExecutionTaskRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("taskId", r.getTaskId());
        m.put("attempt", r.getAttempt());
        m.put("status", r.getStatus());
        m.put("input", parseJsonSafe(r.getInputJson()));
        m.put("output", parseJsonSafe(r.getOutputJson()));
        m.put("errorMessage", r.getErrorMessage());
        m.put("startedAt", r.getStartedAt());
        m.put("completedAt", r.getCompletedAt());
        return m;
    }
}
