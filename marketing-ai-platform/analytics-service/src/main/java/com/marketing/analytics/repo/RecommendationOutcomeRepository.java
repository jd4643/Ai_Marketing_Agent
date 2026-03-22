package com.marketing.analytics.repo;

import com.marketing.analytics.model.RecommendationOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationOutcomeRepository extends JpaRepository<RecommendationOutcome, UUID> {

    Optional<RecommendationOutcome> findByRecommendationId(UUID recommendationId);

    List<RecommendationOutcome> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<RecommendationOutcome> findByBusinessIdAndOutcomeVerdictOrderByCreatedAtDesc(UUID businessId, String verdict);

    List<RecommendationOutcome> findByOutcomeVerdictAndActionDateBefore(String verdict, Instant cutoff);

    List<RecommendationOutcome> findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID businessId, Instant since);
}
