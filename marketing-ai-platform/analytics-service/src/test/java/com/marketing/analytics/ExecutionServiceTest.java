package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.ExecutionPlan;
import com.marketing.analytics.model.ExecutionTask;
import com.marketing.analytics.model.ExecutionTaskRun;
import com.marketing.analytics.repo.ExecutionPlanRepository;
import com.marketing.analytics.repo.ExecutionTaskRepository;
import com.marketing.analytics.repo.ExecutionTaskRunRepository;
import com.marketing.analytics.service.ExecutionService;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock ExecutionPlanRepository planRepo;
    @Mock ExecutionTaskRepository taskRepo;
    @Mock ExecutionTaskRunRepository runRepo;
    @InjectMocks ExecutionService service;

    private static final UUID BIZ_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxActivePlans", 5);
        ReflectionTestUtils.setField(service, "defaultMaxRetries", 3);
    }

    private ExecutionPlan buildPlan(String status, int totalTasks) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId(UUID.randomUUID());
        plan.setBusinessId(BIZ_ID);
        plan.setName("Test Plan");
        plan.setSourceType("MANUAL");
        plan.setStatus(status);
        plan.setTotalTasks(totalTasks);
        plan.setCompletedTasks(0);
        plan.setFailedTasks(0);
        plan.setSkippedTasks(0);
        plan.setVersion(1);
        plan.setCreatedAt(Instant.now());
        plan.setUpdatedAt(Instant.now());
        return plan;
    }

    private ExecutionTask buildTask(UUID planId, String status, String taskType) {
        ExecutionTask task = new ExecutionTask();
        task.setId(UUID.randomUUID());
        task.setPlanId(planId);
        task.setBusinessId(BIZ_ID);
        task.setTaskType(taskType);
        task.setName("Test Task");
        task.setStatus(status);
        task.setPriority(0);
        task.setSequenceOrder(0);
        task.setMaxRetries(3);
        task.setRetryCount(0);
        task.setVersion(1);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    // ─── Plan Creation ──────────────────────────────────────────────────

    @Nested class CreatePlanTests {

        @Test void createManualPlanSuccess() {
            when(planRepo.countActivePlans(BIZ_ID)).thenReturn(0L);
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> request = new HashMap<>();
            request.put("name", "My Plan");
            request.put("tasks", List.of(
                    Map.of("name", "Task 1", "taskType", "SETUP"),
                    Map.of("name", "Task 2", "taskType", "CREATE_CREATIVE")
            ));

            Map<String, Object> result = service.createManualPlan(BIZ_ID, request);

            assertEquals("My Plan", result.get("name"));
            assertEquals("DRAFT", result.get("status"));
            assertEquals(2, result.get("totalTasks"));
            verify(planRepo, times(2)).save(any());
            verify(taskRepo, times(2)).save(any());
        }

        @Test void createManualPlanEmptyTasksThrows() {
            Map<String, Object> request = new HashMap<>();
            request.put("tasks", List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> service.createManualPlan(BIZ_ID, request));
        }

        @Test void createManualPlanInvalidTaskTypeThrows() {
            when(planRepo.countActivePlans(BIZ_ID)).thenReturn(0L);
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> request = new HashMap<>();
            request.put("tasks", List.of(
                    Map.of("name", "Bad Task", "taskType", "INVALID_TYPE")
            ));

            assertThrows(IllegalArgumentException.class,
                    () -> service.createManualPlan(BIZ_ID, request));
        }

        @Test void createPlanExceedsLimitThrows() {
            when(planRepo.countActivePlans(BIZ_ID)).thenReturn(5L);

            Map<String, Object> request = new HashMap<>();
            request.put("tasks", List.of(Map.of("name", "T1", "taskType", "SETUP")));

            assertThrows(IllegalArgumentException.class,
                    () -> service.createManualPlan(BIZ_ID, request));
        }

        @Test void createPlanFromStrategyIdempotent() {
            UUID strategyRunId = UUID.randomUUID();
            ExecutionPlan existing = buildPlan("DRAFT", 3);
            when(planRepo.findByBusinessIdAndStrategyRunId(BIZ_ID, strategyRunId))
                    .thenReturn(Optional.of(existing));

            Map<String, Object> request = new HashMap<>();
            request.put("strategyRunId", strategyRunId.toString());

            Map<String, Object> result = service.createPlanFromStrategy(BIZ_ID, request);

            assertEquals(existing.getId(), result.get("id"));
            verify(planRepo, never()).save(any());
        }

        @Test void createPlanFromStrategyWithChecklist() {
            when(planRepo.countActivePlans(BIZ_ID)).thenReturn(0L);
            when(planRepo.findByBusinessIdAndStrategyRunId(any(), any())).thenReturn(Optional.empty());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> strategy = new HashMap<>();
            strategy.put("setupChecklist", "1. Create Facebook page\n2. Set up pixel\n3. Install GTM");
            strategy.put("creativesNeeded", "- Hero video\n- Carousel images");

            Map<String, Object> request = new HashMap<>();
            request.put("strategyRunId", UUID.randomUUID().toString());
            request.put("strategy", strategy);

            Map<String, Object> result = service.createPlanFromStrategy(BIZ_ID, request);

            assertEquals("DRAFT", result.get("status"));
            // 3 setup + 2 creative + 1 review = 6
            assertEquals(6, result.get("totalTasks"));
        }

        @Test void createPlanTaskNameRequired() {
            when(planRepo.countActivePlans(BIZ_ID)).thenReturn(0L);
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> request = new HashMap<>();
            request.put("tasks", List.of(Map.of("taskType", "SETUP")));

            assertThrows(IllegalArgumentException.class,
                    () -> service.createManualPlan(BIZ_ID, request));
        }
    }

    // ─── Plan Lifecycle ─────────────────────────────────────────────────

    @Nested class PlanLifecycleTests {

        @Test void activateDraftPlan() {
            ExecutionPlan plan = buildPlan("DRAFT", 3);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.activatePlan(plan.getId());

            assertEquals("ACTIVE", result.get("status"));
            verify(planRepo).save(plan);
        }

        @Test void activateAlreadyActivePlanIdempotent() {
            ExecutionPlan plan = buildPlan("ACTIVE", 3);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            Map<String, Object> result = service.activatePlan(plan.getId());

            assertEquals("ACTIVE", result.get("status"));
            verify(planRepo, never()).save(any());
        }

        @Test void activateCompletedPlanThrows() {
            ExecutionPlan plan = buildPlan("COMPLETED", 3);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            assertThrows(IllegalArgumentException.class,
                    () -> service.activatePlan(plan.getId()));
        }

        @Test void activateEmptyPlanThrows() {
            ExecutionPlan plan = buildPlan("DRAFT", 0);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            assertThrows(IllegalArgumentException.class,
                    () -> service.activatePlan(plan.getId()));
        }

        @Test void cancelActivePlan() {
            ExecutionPlan plan = buildPlan("ACTIVE", 3);
            List<ExecutionTask> pending = List.of(buildTask(plan.getId(), "PENDING", "SETUP"));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.findByPlanIdAndStatusOrderBySequenceOrder(plan.getId(), "PENDING")).thenReturn(pending);
            when(taskRepo.findByPlanIdAndStatusOrderBySequenceOrder(plan.getId(), "BLOCKED")).thenReturn(List.of());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.cancelPlan(plan.getId());

            assertEquals("CANCELLED", result.get("status"));
            assertEquals("SKIPPED", pending.get(0).getStatus());
        }

        @Test void cancelAlreadyCancelledIdempotent() {
            ExecutionPlan plan = buildPlan("CANCELLED", 3);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            Map<String, Object> result = service.cancelPlan(plan.getId());

            assertEquals("CANCELLED", result.get("status"));
            verify(planRepo, never()).save(any());
        }

        @Test void cancelCompletedPlanThrows() {
            ExecutionPlan plan = buildPlan("COMPLETED", 3);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            assertThrows(IllegalArgumentException.class,
                    () -> service.cancelPlan(plan.getId()));
        }

        @Test void getPlanNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(planRepo.findById(id)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.getPlan(id));
        }

        @Test void listPlansFilterByStatus() {
            ExecutionPlan plan = buildPlan("ACTIVE", 2);
            when(planRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ_ID, "ACTIVE"))
                    .thenReturn(List.of(plan));

            List<Map<String, Object>> result = service.listPlans(BIZ_ID, "ACTIVE");

            assertEquals(1, result.size());
            assertEquals("ACTIVE", result.get(0).get("status"));
        }

        @Test void listPlansNoFilter() {
            when(planRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ_ID)).thenReturn(List.of());

            List<Map<String, Object>> result = service.listPlans(BIZ_ID, null);

            assertTrue(result.isEmpty());
        }
    }

    // ─── Task Lifecycle ─────────────────────────────────────────────────

    @Nested class TaskLifecycleTests {

        @Test void startPendingTask() {
            ExecutionPlan plan = buildPlan("ACTIVE", 3);
            ExecutionTask task = buildTask(plan.getId(), "PENDING", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.startTask(task.getId());

            assertEquals("IN_PROGRESS", result.get("status"));
            assertNotNull(result.get("runId"));
            verify(runRepo).save(any());
        }

        @Test void startTaskIdempotent() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 3);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            Map<String, Object> result = service.startTask(task.getId());

            assertEquals("IN_PROGRESS", result.get("status"));
            verify(runRepo, never()).save(any());
        }

        @Test void startCompletedTaskThrows() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 3);
            ExecutionTask task = buildTask(plan.getId(), "COMPLETED", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            assertThrows(IllegalArgumentException.class,
                    () -> service.startTask(task.getId()));
        }

        @Test void startTaskOnDraftPlanThrows() {
            ExecutionPlan plan = buildPlan("DRAFT", 3);
            ExecutionTask task = buildTask(plan.getId(), "PENDING", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));

            assertThrows(IllegalArgumentException.class,
                    () -> service.startTask(task.getId()));
        }

        @Test void startTaskWithUnmetDependencyThrows() {
            ExecutionPlan plan = buildPlan("ACTIVE", 3);
            ExecutionTask dep = buildTask(plan.getId(), "PENDING", "SETUP");
            ExecutionTask task = buildTask(plan.getId(), "PENDING", "CREATE_CREATIVE");
            task.setDependsOnTaskId(dep.getId());

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.findById(dep.getId())).thenReturn(Optional.of(dep));

            assertThrows(IllegalArgumentException.class,
                    () -> service.startTask(task.getId()));
        }

        @Test void startTaskWithCompletedDependency() {
            ExecutionPlan plan = buildPlan("ACTIVE", 3);
            ExecutionTask dep = buildTask(plan.getId(), "COMPLETED", "SETUP");
            ExecutionTask task = buildTask(plan.getId(), "PENDING", "CREATE_CREATIVE");
            task.setDependsOnTaskId(dep.getId());

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.findById(dep.getId())).thenReturn(Optional.of(dep));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.startTask(task.getId());

            assertEquals("IN_PROGRESS", result.get("status"));
        }

        @Test void completeInProgressTask() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 1);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            Map<String, Object> result = service.completeTask(task.getId(), Map.of("output", "done"));

            assertEquals("COMPLETED", result.get("status"));
        }

        @Test void completeTaskIdempotent() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "COMPLETED", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            Map<String, Object> result = service.completeTask(task.getId(), Map.of());

            assertEquals("COMPLETED", result.get("status"));
            verify(taskRepo, never()).save(any());
        }

        @Test void completePendingTaskThrows() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "PENDING", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            assertThrows(IllegalArgumentException.class,
                    () -> service.completeTask(task.getId(), Map.of()));
        }

        @Test void failTaskWithRetriesRemaining() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 3);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            task.setRetryCount(0);
            task.setMaxRetries(3);
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());

            Map<String, Object> result = service.failTask(task.getId(), "Connection timeout");

            assertEquals("PENDING", result.get("status"));
            assertEquals(1, task.getRetryCount());
        }

        @Test void failTaskRetriesExhausted() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 1);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            task.setRetryCount(2);
            task.setMaxRetries(3);
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());
            when(taskRepo.findByDependsOnTaskId(task.getId())).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            Map<String, Object> result = service.failTask(task.getId(), "Permanent failure");

            assertEquals("FAILED", result.get("status"));
            assertEquals(3, task.getRetryCount());
        }

        @Test void failTaskBlocksDependents() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 2);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            task.setRetryCount(2);
            task.setMaxRetries(3);

            ExecutionTask dependent = buildTask(plan.getId(), "PENDING", "CREATE_CREATIVE");
            dependent.setDependsOnTaskId(task.getId());

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());
            when(taskRepo.findByDependsOnTaskId(task.getId())).thenReturn(List.of(dependent));
            when(taskRepo.findByDependsOnTaskId(dependent.getId())).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(2L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            service.failTask(task.getId(), "Failure");

            assertEquals("BLOCKED", dependent.getStatus());
        }

        @Test void failTaskIdempotent() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "FAILED", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            Map<String, Object> result = service.failTask(task.getId(), "Error");

            assertEquals("FAILED", result.get("status"));
            verify(taskRepo, never()).save(any());
        }

        @Test void skipPendingTask() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 1);
            ExecutionTask task = buildTask(plan.getId(), "PENDING", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(1L);

            Map<String, Object> result = service.skipTask(task.getId());

            assertEquals("SKIPPED", result.get("status"));
        }

        @Test void skipCompletedTaskThrows() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "COMPLETED", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            assertThrows(IllegalArgumentException.class,
                    () -> service.skipTask(task.getId()));
        }

        @Test void skipInProgressTaskThrows() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "IN_PROGRESS", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            assertThrows(IllegalArgumentException.class,
                    () -> service.skipTask(task.getId()));
        }

        @Test void retryFailedTask() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 1);
            ExecutionTask task = buildTask(plan.getId(), "FAILED", "SETUP");
            task.setRetryCount(3);
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.findByDependsOnTaskId(task.getId())).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            Map<String, Object> result = service.retryTask(task.getId());

            assertEquals("PENDING", result.get("status"));
            assertEquals(0, task.getRetryCount());
        }

        @Test void retryNonFailedTaskThrows() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "PENDING", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));

            assertThrows(IllegalArgumentException.class,
                    () -> service.retryTask(task.getId()));
        }

        @Test void retryUnblocksDependent() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 2);
            ExecutionTask task = buildTask(plan.getId(), "FAILED", "SETUP");
            ExecutionTask dependent = buildTask(plan.getId(), "BLOCKED", "CREATE_CREATIVE");
            dependent.setDependsOnTaskId(task.getId());

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepo.findByDependsOnTaskId(task.getId())).thenReturn(List.of(dependent));
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(2L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            service.retryTask(task.getId());

            assertEquals("PENDING", dependent.getStatus());
        }

        @Test void taskNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(taskRepo.findById(id)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.startTask(id));
        }
    }

    // ─── Plan Auto-Completion ───────────────────────────────────────────

    @Nested class PlanAutoCompletionTests {

        @Test void planCompletesWhenAllTasksDone() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 2);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(2L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(2L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(0L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            service.completeTask(task.getId(), Map.of());

            assertEquals("COMPLETED", plan.getStatus());
            assertNotNull(plan.getCompletedAt());
        }

        @Test void planMarkedFailedWhenHasFailedTasks() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 2);
            ExecutionTask task = buildTask(plan.getId(), "IN_PROGRESS", "SETUP");
            task.setRetryCount(2);
            task.setMaxRetries(3);

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(runRepo.findByTaskIdAndStatus(task.getId(), "RUNNING")).thenReturn(List.of());
            when(taskRepo.findByDependsOnTaskId(task.getId())).thenReturn(List.of());
            when(taskRepo.countByPlanId(plan.getId())).thenReturn(2L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "COMPLETED")).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "FAILED")).thenReturn(1L);
            when(taskRepo.countByPlanIdAndStatus(plan.getId(), "SKIPPED")).thenReturn(0L);

            service.failTask(task.getId(), "Error");

            assertEquals("FAILED", plan.getStatus());
        }
    }

    // ─── Task Detail ────────────────────────────────────────────────────

    @Nested class TaskDetailTests {

        @Test void getTaskDetailWithRuns() {
            ExecutionTask task = buildTask(UUID.randomUUID(), "COMPLETED", "SETUP");
            ExecutionTaskRun run = new ExecutionTaskRun();
            run.setId(UUID.randomUUID());
            run.setTaskId(task.getId());
            run.setAttempt(1);
            run.setStatus("SUCCEEDED");
            run.setStartedAt(Instant.now());

            when(taskRepo.findById(task.getId())).thenReturn(Optional.of(task));
            when(runRepo.findByTaskIdOrderByAttemptDesc(task.getId())).thenReturn(List.of(run));

            Map<String, Object> result = service.getTaskDetail(task.getId());

            assertEquals("COMPLETED", result.get("status"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> runs = (List<Map<String, Object>>) result.get("runs");
            assertEquals(1, runs.size());
            assertEquals("SUCCEEDED", runs.get(0).get("status"));
        }
    }

    // ─── Progress Calculation ───────────────────────────────────────────

    @Nested class ProgressTests {

        @Test void progressCalculation() {
            ExecutionPlan plan = buildPlan("IN_PROGRESS", 4);
            plan.setCompletedTasks(2);
            plan.setSkippedTasks(1);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.findByPlanIdOrderBySequenceOrder(plan.getId())).thenReturn(List.of());

            Map<String, Object> result = service.getPlan(plan.getId());

            assertEquals(75L, result.get("progress"));
        }

        @Test void progressZeroWhenNoTasks() {
            ExecutionPlan plan = buildPlan("DRAFT", 0);
            when(planRepo.findById(plan.getId())).thenReturn(Optional.of(plan));
            when(taskRepo.findByPlanIdOrderBySequenceOrder(plan.getId())).thenReturn(List.of());

            Map<String, Object> result = service.getPlan(plan.getId());

            assertEquals(0L, result.get("progress"));
        }
    }
}
