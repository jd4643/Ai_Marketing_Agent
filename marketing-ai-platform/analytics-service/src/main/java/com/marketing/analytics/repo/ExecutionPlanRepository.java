package com.marketing.analytics.repo;

import com.marketing.analytics.model.ExecutionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionPlanRepository extends JpaRepository<ExecutionPlan, UUID> {

    List<ExecutionPlan> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<ExecutionPlan> findByBusinessIdAndStatusOrderByCreatedAtDesc(UUID businessId, String status);

    Optional<ExecutionPlan> findByBusinessIdAndStrategyRunId(UUID businessId, UUID strategyRunId);

    @Query(value = "SELECT COUNT(*) FROM execution_plans WHERE business_id = :businessId AND status IN ('ACTIVE','IN_PROGRESS')", nativeQuery = true)
    long countActivePlans(@Param("businessId") UUID businessId);
}
