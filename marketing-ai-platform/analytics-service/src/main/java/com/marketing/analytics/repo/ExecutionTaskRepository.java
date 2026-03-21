package com.marketing.analytics.repo;

import com.marketing.analytics.model.ExecutionTask;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionTaskRepository extends JpaRepository<ExecutionTask, UUID> {

    List<ExecutionTask> findByPlanIdOrderBySequenceOrder(UUID planId);

    List<ExecutionTask> findByPlanIdAndStatusOrderBySequenceOrder(UUID planId, String status);

    List<ExecutionTask> findByBusinessIdAndStatus(UUID businessId, String status);

    List<ExecutionTask> findByDependsOnTaskId(UUID dependsOnTaskId);

    @Query(value = "SELECT COUNT(*) FROM execution_tasks WHERE plan_id = :planId AND status = :status", nativeQuery = true)
    long countByPlanIdAndStatus(@Param("planId") UUID planId, @Param("status") String status);

    @Query(value = "SELECT COUNT(*) FROM execution_tasks WHERE plan_id = :planId", nativeQuery = true)
    long countByPlanId(@Param("planId") UUID planId);

    @Query(value = """
        SELECT t.* FROM execution_tasks t
        WHERE t.plan_id = :planId
          AND t.status = 'PENDING'
          AND (t.depends_on_task_id IS NULL
               OR EXISTS (SELECT 1 FROM execution_tasks dep
                          WHERE dep.id = t.depends_on_task_id
                            AND dep.status IN ('COMPLETED','SKIPPED')))
        ORDER BY t.sequence_order
        """, nativeQuery = true)
    List<ExecutionTask> findReadyTasks(@Param("planId") UUID planId);
}
