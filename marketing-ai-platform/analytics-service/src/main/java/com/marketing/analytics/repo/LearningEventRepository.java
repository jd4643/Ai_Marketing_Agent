package com.marketing.analytics.repo;

import com.marketing.analytics.model.LearningEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningEventRepository extends JpaRepository<LearningEvent, UUID> {

    List<LearningEvent> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<LearningEvent> findByBusinessIdAndEventTypeOrderByCreatedAtDesc(UUID businessId, String eventType);

    List<LearningEvent> findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID businessId, Instant since);

    List<LearningEvent> findByBusinessIdAndSeverityOrderByCreatedAtDesc(UUID businessId, String severity);

    long countByBusinessIdAndEventTypeAndCreatedAtAfter(UUID businessId, String eventType, Instant since);
}
