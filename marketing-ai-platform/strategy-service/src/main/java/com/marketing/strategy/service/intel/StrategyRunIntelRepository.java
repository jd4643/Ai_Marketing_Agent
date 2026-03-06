package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRunIntelRepository extends JpaRepository<StrategyRunIntelEntity, UUID> {
  List<StrategyRunIntelEntity> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
