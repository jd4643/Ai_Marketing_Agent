package com.marketing.strategy.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy response expanded to behave like a full marketing playbook.
 * <p>
 * Original fields ({@code requestId}, {@code strategyVersion}, {@code platformBudgetSplit},
 * {@code campaignPlan}, {@code funnelStrategy}, {@code expectedCPL}, {@code expectedROAS},
 * {@code reasoning}, {@code assumptions}) are kept for backward compatibility.
 * <p>
 * New optional consultant-style sections are additive — existing clients that ignore
 * unknown JSON keys will continue to work unchanged.
 */
public record StrategyResponse(
        // --- existing required fields (backward-compatible) ---
        UUID requestId,
        String strategyVersion,
        Map<String, BigDecimal> platformBudgetSplit,
        List<Map<String, Object>> campaignPlan,
        String funnelStrategy,
        String expectedCPL,
        String expectedROAS,
        String reasoning,
        List<String> assumptions,

        // --- new consultant-style playbook sections (optional / nullable) ---

        /** Short business-specific summary using actual business facts. */
        Map<String, Object> businessSnapshot,

        /** Competition context, demand/trend context, customer buying behavior, seasonal considerations. */
        Map<String, Object> marketAnalysis,

        /** Primary buyer, motivations, pain points, triggers. */
        Map<String, Object> customerPersona,

        /** Why chosen platforms fit this business, why others are lower priority. */
        Map<String, Object> whyThisStrategy,

        /** Per-platform details: why chosen, objective, budget, duration, audience, creative format, success metric. */
        List<Map<String, Object>> platformStrategy,

        /** Per-platform campaign names/themes, ad groups, keyword direction, retargeting logic. */
        List<Map<String, Object>> campaignArchitecture,

        /** Creative angles, hooks, messaging style, content types, examples. */
        Map<String, Object> creativeStrategy,

        /** Exact assets the business should prepare — image/video/carousel/reel/testimonial requirements. */
        List<Map<String, Object>> creativesNeeded,

        /** Week-by-week execution roadmap (weeks 1-4), what to launch, measure, change. */
        Map<String, Object> executionRoadmap,

        /** Tracking setup, account setup, landing page readiness, conversion events, etc. */
        List<Map<String, Object>> setupChecklist,

        /** What page to send traffic to and what conversion elements it must contain. */
        Map<String, Object> landingPageRecommendations,

        /** What offer/promotion/CTA should be used. */
        Map<String, Object> offerStrategy,

        /** KPI targets, evaluation windows, stop-loss rules, scaling rules. */
        Map<String, Object> measurementPlan,

        /** Likely problems and what to do if performance is weak. */
        List<Map<String, Object>> risksAndMitigations,

        /** Required for cold-start / new users — data to collect, decisions that should wait. */
        Map<String, Object> first14DaysLearningPlan,

        /** Business-friendly consultant-style markdown explanation for UI rendering. */
        String humanReadablePlanMarkdown,

        /** Creative asset winner insights — top-performing asset details with classification. */
        List<Map<String, Object>> winnerInsights,

        /** Optimization signals from weak/underperforming assets. */
        List<String> optimizationSignals,

        /** Suggested next creative moves based on winner/loser analysis. */
        List<String> recommendedNextCreativeMoves
) {}

