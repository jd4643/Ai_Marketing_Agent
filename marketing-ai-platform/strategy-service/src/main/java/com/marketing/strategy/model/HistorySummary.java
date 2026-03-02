package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HistorySummary(UUID requestId, String objective, BigDecimal monthlyBudget, String status,
                             Instant createdAt) {
}
