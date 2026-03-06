package com.marketing.strategy.service.intel;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyTemplateRepository extends JpaRepository<StrategyTemplateEntity, UUID> {
    Optional<StrategyTemplateEntity> findByTemplateKey(String templateKey);
}
