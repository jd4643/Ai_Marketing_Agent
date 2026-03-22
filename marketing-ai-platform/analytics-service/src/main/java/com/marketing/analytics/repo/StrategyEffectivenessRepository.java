package com.marketing.analytics.repo;

import com.marketing.analytics.model.StrategyEffectiveness;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyEffectivenessRepository extends JpaRepository<StrategyEffectiveness, UUID> {

    List<StrategyEffectiveness> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<StrategyEffectiveness> findByStrategyRunIdOrderByCreatedAtDesc(UUID strategyRunId);

    List<StrategyEffectiveness> findByBusinessIdAndRecommendedActionOrderByCreatedAtDesc(UUID businessId, String action);
}
