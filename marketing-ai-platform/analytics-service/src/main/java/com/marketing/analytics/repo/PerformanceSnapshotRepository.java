package com.marketing.analytics.repo;

import com.marketing.analytics.model.PerformanceSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceSnapshotRepository extends JpaRepository<PerformanceSnapshot, UUID> {

    List<PerformanceSnapshot> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<PerformanceSnapshot> findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID businessId, Instant since);

    List<PerformanceSnapshot> findByPlanIdOrderByCreatedAtDesc(UUID planId);

    List<PerformanceSnapshot> findByBusinessIdAndSnapshotTypeOrderByCreatedAtDesc(UUID businessId, String snapshotType);
}
