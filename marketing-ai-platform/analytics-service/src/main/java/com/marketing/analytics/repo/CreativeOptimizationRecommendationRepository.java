package com.marketing.analytics.repo;

import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativeOptimizationRecommendationRepository extends JpaRepository<CreativeOptimizationRecommendation, UUID> {

    List<CreativeOptimizationRecommendation> findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID businessId, Instant since);

    List<CreativeOptimizationRecommendation> findByBusinessIdAndStatusOrderByCreatedAtDesc(UUID businessId, String status);

    List<CreativeOptimizationRecommendation> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<CreativeOptimizationRecommendation> findByBusinessIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(UUID businessId, String status, Instant since);

    void deleteByBusinessIdAndStatus(UUID businessId, String status);
}
