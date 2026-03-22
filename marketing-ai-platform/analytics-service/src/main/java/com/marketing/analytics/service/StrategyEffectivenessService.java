package com.marketing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.StrategyEffectiveness;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.repo.StrategyEffectivenessRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates how well a generated strategy performs over time.
 * Computes freshness scores, detects staleness signals, and recommends
 * whether to KEEP, REFRESH, or REGENERATE a strategy.
 */
@Service
public class StrategyEffectivenessService {

    private static final Logger log = LoggerFactory.getLogger(StrategyEffectivenessService.class);
    private static final int MAX_FRESHNESS_AGE_DAYS = 30;

    private final DataSource ds;
    private final StrategyEffectivenessRepository effectivenessRepo;
    private final CampaignMetricRepository metricRepo;
    private final CreativeAssetPerformanceRepository perfRepo;
    private final CreativeOptimizationRecommendationRepository recRepo;
    private final LearningEventService learningEventService;
    private final ObjectMapper om = new ObjectMapper();

    public StrategyEffectivenessService(DataSource ds,
                                        StrategyEffectivenessRepository effectivenessRepo,
                                        CampaignMetricRepository metricRepo,
                                        CreativeAssetPerformanceRepository perfRepo,
                                        CreativeOptimizationRecommendationRepository recRepo,
                                        LearningEventService learningEventService) {
        this.ds = ds;
        this.effectivenessRepo = effectivenessRepo;
        this.metricRepo = metricRepo;
        this.perfRepo = perfRepo;
        this.recRepo = recRepo;
        this.learningEventService = learningEventService;
    }

    /**
     * Evaluate the effectiveness of the most recent strategy for a business.
     * Captures current metrics, computes freshness, detects staleness signals.
     */
    @Transactional
    public Map<String, Object> evaluateLatestStrategy(UUID businessId) {
        String requestId = MDC.get("requestId");

        // Fetch latest strategy run from strategy_history
        Map<String, Object> latestStrategy = fetchLatestStrategy(businessId);
        if (latestStrategy == null || latestStrategy.isEmpty()) {
            return Map.of("businessId", businessId, "status", "NO_STRATEGY",
                    "message", "No strategy found for this business");
        }

        UUID strategyRunId = (UUID) latestStrategy.get("requestId");
        Instant strategyCreatedAt = (Instant) latestStrategy.get("createdAt");

        Map<String, Object> currentMetrics = aggregateCurrentMetrics(businessId);
        List<String> signals = detectStalenessSignals(businessId, latestStrategy, currentMetrics);
        double freshness = computeFreshnessScore(strategyCreatedAt, signals);
        String recommendedAction = deriveAction(freshness, signals);

        StrategyEffectiveness eff = new StrategyEffectiveness();
        eff.setId(UUID.randomUUID());
        eff.setStrategyRunId(strategyRunId);
        eff.setBusinessId(businessId);
        eff.setEvaluationType("PERIODIC");
        eff.setMetricsAtEvaluation(toJsonSafe(currentMetrics));
        eff.setFreshnessScore(freshness);
        eff.setStalenessSignals(toJsonSafe(signals));
        eff.setRecommendedAction(recommendedAction);
        eff.setCreatedAt(Instant.now());
        effectivenessRepo.save(eff);

        log.info("Strategy effectiveness evaluated: business={} strategy={} freshness={} action={} requestId={}",
                businessId, strategyRunId, freshness, recommendedAction, requestId);

        if ("REGENERATE".equals(recommendedAction) || "REFRESH".equals(recommendedAction)) {
            learningEventService.record(businessId, "STRATEGY_STALE", "STRATEGY",
                    strategyRunId, Map.of(
                            "freshnessScore", freshness,
                            "recommendedAction", recommendedAction,
                            "signals", signals
                    ), freshness < 30 ? "WARNING" : "INFO");
        }

        return effectivenessToMap(eff, latestStrategy);
    }

    /**
     * Get effectiveness history for a business.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(UUID businessId) {
        return effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().map(e -> effectivenessToMap(e, null)).toList();
    }

    /**
     * Get the latest freshness score for dashboard display.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLatestFreshness(UUID businessId) {
        List<StrategyEffectiveness> list = effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        if (list.isEmpty()) {
            return Map.of("businessId", businessId, "freshnessScore", -1,
                    "recommendedAction", "EVALUATE", "message", "No evaluation yet");
        }
        StrategyEffectiveness latest = list.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);
        result.put("strategyRunId", latest.getStrategyRunId());
        result.put("freshnessScore", latest.getFreshnessScore());
        result.put("recommendedAction", latest.getRecommendedAction());
        result.put("evaluatedAt", latest.getCreatedAt());
        result.put("stalenessSignals", parseJsonSafe(latest.getStalenessSignals()));
        return result;
    }

    // ─── Freshness Score ────────────────────────────────────────────────────

    /**
     * Compute freshness score (0-100) based on strategy age and staleness signals.
     * - Age decay: loses ~3.3 points per day (goes to 0 at 30 days)
     * - Each staleness signal deducts additional points
     */
    public double computeFreshnessScore(Instant createdAt, List<String> signals) {
        long ageDays = ChronoUnit.DAYS.between(createdAt, Instant.now());
        double ageFreshness = Math.max(0, 100.0 - (ageDays * 100.0 / MAX_FRESHNESS_AGE_DAYS));

        // Deduct for signals
        double signalPenalty = signals.size() * 8.0;
        double freshness = Math.max(0, ageFreshness - signalPenalty);

        return Math.round(freshness * 10.0) / 10.0;
    }

    // ─── Staleness Detection ────────────────────────────────────────────────

    public List<String> detectStalenessSignals(UUID businessId, Map<String, Object> strategy,
                                               Map<String, Object> currentMetrics) {
        List<String> signals = new ArrayList<>();

        Instant strategyDate = (Instant) strategy.get("createdAt");
        long ageDays = ChronoUnit.DAYS.between(strategyDate, Instant.now());

        // Signal: Strategy is old
        if (ageDays > 14) {
            signals.add("STRATEGY_AGE: Strategy is " + ageDays + " days old (>14 days)");
        }

        // Signal: High STOP recommendation density
        long stopCount = recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, "OPEN")
                .stream().filter(r -> "STOP".equals(r.getRecommendationType())).count();
        if (stopCount >= 3) {
            signals.add("HIGH_STOP_DENSITY: " + stopCount + " STOP recommendations open (≥3)");
        }

        // Signal: ROAS decline check against strategy assumptions
        Object strategyBudget = strategy.get("monthlyBudget");
        BigDecimal currentRoas = toBd(currentMetrics.get("overallRoas"));
        if (currentRoas.compareTo(BigDecimal.ONE) < 0 && currentRoas.compareTo(BigDecimal.ZERO) > 0) {
            signals.add("LOW_ROAS: Current ROAS=" + currentRoas + " is below 1.0");
        }

        // Signal: Many weak assets
        Object assetClassifications = currentMetrics.get("assetClassifications");
        if (assetClassifications instanceof Map<?, ?> cls) {
            long weakCount = toLng(cls.get("WEAK"));
            long totalAssets = toLng(currentMetrics.get("totalAssets"));
            if (totalAssets > 0 && weakCount > 0 && (double) weakCount / totalAssets > 0.5) {
                signals.add("HIGH_WEAK_RATIO: " + weakCount + "/" + totalAssets + " assets are WEAK (>50%)");
            }
        }

        // Signal: No conversions in last 7 days
        BigDecimal conversions = toBd(currentMetrics.get("totalConversions"));
        if (conversions.compareTo(BigDecimal.ZERO) == 0) {
            signals.add("ZERO_CONVERSIONS: No conversions recorded in last 30 days");
        }

        // Signal: Too many open recommendations (strategy not being actioned)
        long openRecs = recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, "OPEN").size();
        if (openRecs >= 5) {
            signals.add("UNACTIONED_RECOMMENDATIONS: " + openRecs + " recommendations not yet applied (≥5)");
        }

        return signals;
    }

    public String deriveAction(double freshness, List<String> signals) {
        if (freshness < 25 || signals.size() >= 4) return "REGENERATE";
        if (freshness < 50 || signals.size() >= 2) return "REFRESH";
        return "KEEP";
    }

    // ─── Data Access ────────────────────────────────────────────────────────

    Map<String, Object> fetchLatestStrategy(UUID businessId) {
        String sql = "SELECT request_id, business_id, objective, monthly_budget, status, created_at " +
                "FROM strategy_history WHERE business_id = ? AND status = 'SUCCESS' " +
                "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, businessId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("requestId", rs.getObject("request_id", UUID.class));
                    m.put("businessId", rs.getObject("business_id", UUID.class));
                    m.put("objective", rs.getString("objective"));
                    m.put("monthlyBudget", rs.getBigDecimal("monthly_budget"));
                    m.put("status", rs.getString("status"));
                    m.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    return m;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest strategy for business={}: {}", businessId, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> aggregateCurrentMetrics(UUID businessId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        try {
            List<Object[]> agg = metricRepo.aggregateByBusinessId(businessId, since);
            if (agg != null && !agg.isEmpty()) {
                Object[] row = agg.get(0);
                metrics.put("totalSpend", toBd(row[0]));
                metrics.put("totalRevenue", toBd(row[1]));
                metrics.put("totalImpressions", toLng(row[2]));
                metrics.put("totalClicks", toLng(row[3]));
                metrics.put("totalConversions", toLng(row[4]));
                BigDecimal spend = toBd(row[0]);
                BigDecimal revenue = toBd(row[1]);
                metrics.put("overallRoas", spend.compareTo(BigDecimal.ZERO) > 0
                        ? revenue.divide(spend, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("Failed to aggregate metrics for strategy effectiveness: {}", e.getMessage());
        }

        try {
            List<Object[]> clsCounts = perfRepo.countByClassification(businessId);
            Map<String, Long> cls = new LinkedHashMap<>();
            long total = 0;
            if (clsCounts != null) {
                for (Object[] row : clsCounts) {
                    String c = row[0] != null ? row[0].toString() : "UNKNOWN";
                    long cnt = toLng(row[1]);
                    cls.put(c, cnt);
                    total += cnt;
                }
            }
            metrics.put("totalAssets", total);
            metrics.put("assetClassifications", cls);
        } catch (Exception e) {
            log.warn("Failed to count asset classifications: {}", e.getMessage());
        }

        metrics.put("capturedAt", Instant.now().toString());
        return metrics;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> effectivenessToMap(StrategyEffectiveness e, Map<String, Object> strategy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("strategyRunId", e.getStrategyRunId());
        m.put("businessId", e.getBusinessId());
        m.put("evaluationType", e.getEvaluationType());
        m.put("freshnessScore", e.getFreshnessScore());
        m.put("recommendedAction", e.getRecommendedAction());
        m.put("stalenessSignals", parseJsonSafe(e.getStalenessSignals()));
        m.put("metricsAtEvaluation", parseJsonSafe(e.getMetricsAtEvaluation()));
        m.put("createdAt", e.getCreatedAt());
        if (strategy != null) {
            m.put("strategyObjective", strategy.get("objective"));
            m.put("strategyCreatedAt", strategy.get("createdAt"));
        }
        return m;
    }

    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return om.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return Map.of("raw", json); }
    }

    private Object parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return om.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(json); }
    }

    private String toJsonSafe(Object obj) {
        try { return om.writeValueAsString(obj); } catch (JsonProcessingException e) { return "{}"; }
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private long toLng(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }
}
