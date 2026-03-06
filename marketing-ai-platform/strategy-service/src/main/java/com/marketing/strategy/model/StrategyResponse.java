package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StrategyResponse(
    UUID requestId,
    String strategyVersion,
    Map<String, BigDecimal> platformBudgetSplit,
    List<Map<String, Object>> campaignPlan,
    String funnelStrategy,
    String expectedCPL,
    String expectedROAS,
    String reasoning,
    List<String> assumptions,
    Map<String, Object> businessSnapshot,
    Map<String, Object> marketAnalysis,
    Map<String, Object> customerPersona,
    Map<String, Object> whyThisStrategy,
    List<Map<String, Object>> platformStrategy,
    Map<String, Object> campaignArchitecture,
    Map<String, Object> creativeStrategy,
    List<Map<String, Object>> creativesNeeded,
    List<Map<String, Object>> executionRoadmap,
    List<String> setupChecklist,
    Map<String, Object> landingPageRecommendations,
    Map<String, Object> offerStrategy,
    Map<String, Object> measurementPlan,
    List<Map<String, Object>> risksAndMitigations,
    List<Map<String, Object>> first14DaysLearningPlan,
    String humanReadablePlanMarkdown
) {}
