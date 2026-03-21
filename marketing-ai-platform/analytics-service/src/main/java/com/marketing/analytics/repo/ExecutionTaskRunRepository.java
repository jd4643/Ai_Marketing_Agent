package com.marketing.analytics.repo;

import com.marketing.analytics.model.ExecutionTaskRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionTaskRunRepository extends JpaRepository<ExecutionTaskRun, UUID> {

    List<ExecutionTaskRun> findByTaskIdOrderByAttemptDesc(UUID taskId);

    List<ExecutionTaskRun> findByTaskIdAndStatus(UUID taskId, String status);
}
