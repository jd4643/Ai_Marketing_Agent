package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StrategyIntelSummary(UUID requestId, String chosenTemplateKey, Integer confidenceScore,
                                   BigDecimal monthlyBudget, String objective, Instant createdAt) {}
