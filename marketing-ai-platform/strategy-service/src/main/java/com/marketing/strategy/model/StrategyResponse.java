package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StrategyResponse(UUID requestId, String strategyVersion, Map<String, BigDecimal> platformBudgetSplit,
                               List<Map<String, Object>> campaignPlan, String funnelStrategy, String expectedCPL,
                               String expectedROAS, String reasoning, List<String> assumptions) {
}
