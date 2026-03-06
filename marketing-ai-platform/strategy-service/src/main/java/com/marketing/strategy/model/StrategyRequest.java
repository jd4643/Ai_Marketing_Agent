package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StrategyRequest(UUID businessId, String objective, BigDecimal monthlyBudget, List<String> trends, String notes) {}
