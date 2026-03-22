package com.marketing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.CreativeAssetPerformance;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.model.RecommendationOutcome;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.repo.RecommendationOutcomeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks recommendation outcomes by capturing baseline metrics at action time
 * and evaluating the impact after a configurable window.
 */
@Service
public class OutcomeTrackingService {

    private static final Logger log = LoggerFactory.getLogger(OutcomeTrackingService.class);
    private static final int DEFAULT_EVALUATION_WINDOW_DAYS = 7;

    private final RecommendationOutcomeRepository outcomeRepo;
    private final CreativeOptimizationRecommendationRepository recRepo;
    private final CampaignMetricRepository metricRepo;
    private final CreativeAssetPerformanceRepository perfRepo;
    private final LearningEventService learningEventService;
    private final ObjectMapper om = new ObjectMapper();

    public OutcomeTrackingService(RecommendationOutcomeRepository outcomeRepo,
                                  CreativeOptimizationRecommendationRepository recRepo,
                                  CampaignMetricRepository metricRepo,
                                  CreativeAssetPerformanceRepository perfRepo,
                                  LearningEventService learningEventService) {
        this.outcomeRepo = outcomeRepo;
        this.recRepo = recRepo;
        this.metricRepo = metricRepo;
        this.perfRepo = perfRepo;
        this.learningEventService = learningEventService;
    }

    /**
     * Record a baseline snapshot when a recommendation is applied.
     * Called from RecommendationActionService.apply().
     */
    @Transactional
    public Map<String, Object> recordBaseline(UUID recommendationId, String actionTaken) {
        String requestId = MDC.get("requestId");

        // Idempotent — skip if already recorded
        Optional<RecommendationOutcome> existing = outcomeRepo.findByRecommendationId(recommendationId);
        if (existing.isPresent()) {
            log.info("Outcome baseline already exists for recommendation={} requestId={}", recommendationId, requestId);
            return outcomeToMap(existing.get());
        }

        CreativeOptimizationRecommendation rec = recRepo.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        Map<String, Object> baseline = captureMetricsSnapshot(rec.getBusinessId(), rec.getCreativeAssetId());

        Instant now = Instant.now();
        RecommendationOutcome outcome = new RecommendationOutcome();
        outcome.setId(UUID.randomUUID());
        outcome.setRecommendationId(recommendationId);
        outcome.setBusinessId(rec.getBusinessId());
        outcome.setActionTaken(actionTaken);
        outcome.setActionDate(now);
        outcome.setBaselineSnapshot(toJsonSafe(baseline));
        outcome.setEvaluationWindowDays(DEFAULT_EVALUATION_WINDOW_DAYS);
        outcome.setOutcomeVerdict("PENDING");
        outcome.setCreatedAt(now);
        outcome.setUpdatedAt(now);
        outcomeRepo.save(outcome);

        log.info("Recorded outcome baseline for recommendation={} action={} requestId={}",
                recommendationId, actionTaken, requestId);

        learningEventService.record(rec.getBusinessId(), "BASELINE_CAPTURED", "RECOMMENDATION",
                recommendationId, Map.of(
                        "action", actionTaken,
                        "recommendationType", rec.getRecommendationType(),
                        "priority", rec.getPriority(),
                        "evaluationWindowDays", DEFAULT_EVALUATION_WINDOW_DAYS
                ), "INFO");

        return outcomeToMap(outcome);
    }

    /**
     * Evaluate all PENDING outcomes whose evaluation window has elapsed.
     * Returns the number of outcomes evaluated.
     */
    @Transactional
    public Map<String, Object> evaluateAllPending() {
        String requestId = MDC.get("requestId");

        // Find all PENDING outcomes where enough time has passed
        Instant now = Instant.now();
        List<RecommendationOutcome> pending = outcomeRepo.findByOutcomeVerdictAndActionDateBefore("PENDING",
                now.minus(1, ChronoUnit.DAYS)); // At least 1 day old

        int evaluated = 0;
        int positive = 0;
        int negative = 0;
        int neutral = 0;

        for (RecommendationOutcome outcome : pending) {
            Instant windowEnd = outcome.getActionDate().plus(outcome.getEvaluationWindowDays(), ChronoUnit.DAYS);
            if (now.isBefore(windowEnd)) continue; // Window hasn't elapsed yet

            try {
                evaluateOutcome(outcome);
                evaluated++;
                switch (outcome.getOutcomeVerdict()) {
                    case "POSITIVE" -> positive++;
                    case "NEGATIVE" -> negative++;
                    default -> neutral++;
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate outcome {} for recommendation={}: {}",
                        outcome.getId(), outcome.getRecommendationId(), e.getMessage());
            }
        }

        log.info("Evaluated {} outcomes (positive={}, negative={}, neutral={}) requestId={}",
                evaluated, positive, negative, neutral, requestId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("evaluated", evaluated);
        result.put("positive", positive);
        result.put("negative", negative);
        result.put("neutral", neutral);
        result.put("pendingRemaining", pending.size() - evaluated);
        return result;
    }

    /**
     * Evaluate a single outcome: capture current metrics, compute deltas, determine verdict.
     */
    private void evaluateOutcome(RecommendationOutcome outcome) {
        CreativeOptimizationRecommendation rec = recRepo.findById(outcome.getRecommendationId()).orElse(null);
        UUID assetId = rec != null ? rec.getCreativeAssetId() : null;

        Map<String, Object> currentSnapshot = captureMetricsSnapshot(outcome.getBusinessId(), assetId);
        Map<String, Object> baseline = parseJsonSafe(outcome.getBaselineSnapshot());
        Map<String, Object> deltas = computeDeltas(baseline, currentSnapshot);
        double impactScore = computeImpactScore(deltas, rec != null ? rec.getRecommendationType() : "UNKNOWN");
        String verdict = deriveVerdict(impactScore);

        Instant now = Instant.now();
        outcome.setOutcomeSnapshot(toJsonSafe(currentSnapshot));
        outcome.setEvaluationDate(now);
        outcome.setDeltaJson(toJsonSafe(deltas));
        outcome.setImpactScore(impactScore);
        outcome.setOutcomeVerdict(verdict);
        outcome.setNotes(buildOutcomeNotes(deltas, impactScore, rec));
        outcome.setUpdatedAt(now);
        outcomeRepo.save(outcome);

        learningEventService.record(outcome.getBusinessId(), "RECOMMENDATION_OUTCOME", "RECOMMENDATION",
                outcome.getRecommendationId(), Map.of(
                        "outcomeId", outcome.getId().toString(),
                        "verdict", verdict,
                        "impactScore", impactScore,
                        "recommendationType", rec != null ? rec.getRecommendationType() : "UNKNOWN"
                ), impactScore < -0.3 ? "WARNING" : "INFO");
    }

    /**
     * Get outcomes for a business, optionally filtered by verdict.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOutcomes(UUID businessId, String verdict) {
        List<RecommendationOutcome> outcomes;
        if (verdict != null && !verdict.isBlank()) {
            outcomes = outcomeRepo.findByBusinessIdAndOutcomeVerdictOrderByCreatedAtDesc(businessId, verdict);
        } else {
            outcomes = outcomeRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        }
        return outcomes.stream().map(this::outcomeToMap).toList();
    }

    /**
     * Get aggregated outcome statistics for a business.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOutcomeStats(UUID businessId) {
        List<RecommendationOutcome> all = outcomeRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);

        long pending = 0, positive = 0, negative = 0, neutral = 0;
        double totalImpact = 0;
        int evaluated = 0;

        for (RecommendationOutcome o : all) {
            switch (o.getOutcomeVerdict()) {
                case "PENDING" -> pending++;
                case "POSITIVE" -> { positive++; totalImpact += o.getImpactScore() != null ? o.getImpactScore() : 0; evaluated++; }
                case "NEGATIVE" -> { negative++; totalImpact += o.getImpactScore() != null ? o.getImpactScore() : 0; evaluated++; }
                default -> { neutral++; totalImpact += o.getImpactScore() != null ? o.getImpactScore() : 0; evaluated++; }
            }
        }

        double successRate = (positive + negative + neutral) > 0
                ? (double) positive / (positive + negative + neutral) * 100
                : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("businessId", businessId);
        stats.put("totalOutcomes", all.size());
        stats.put("pending", pending);
        stats.put("positive", positive);
        stats.put("negative", negative);
        stats.put("neutral", neutral);
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        stats.put("avgImpactScore", evaluated > 0 ? Math.round(totalImpact / evaluated * 1000.0) / 1000.0 : 0);
        return stats;
    }

    // ─── Metrics Snapshot ───────────────────────────────────────────────────

    private Map<String, Object> captureMetricsSnapshot(UUID businessId, UUID assetId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        // Business-level campaign metrics
        try {
            List<Object[]> agg = metricRepo.aggregateByBusinessId(businessId, since);
            if (agg != null && !agg.isEmpty()) {
                Object[] row = agg.get(0);
                snapshot.put("totalSpend", toBd(row[0]));
                snapshot.put("totalRevenue", toBd(row[1]));
                snapshot.put("totalImpressions", toLng(row[2]));
                snapshot.put("totalClicks", toLng(row[3]));
                snapshot.put("totalConversions", toLng(row[4]));
                BigDecimal spend = toBd(row[0]);
                BigDecimal revenue = toBd(row[1]);
                snapshot.put("overallRoas", spend.compareTo(BigDecimal.ZERO) > 0
                        ? revenue.divide(spend, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("Failed to aggregate campaign metrics for baseline: {}", e.getMessage());
        }

        // Asset-specific performance if available
        if (assetId != null) {
            try {
                perfRepo.findById(assetId).ifPresent(perf -> {
                    snapshot.put("assetClassification", perf.getClassification());
                    snapshot.put("assetPerformanceScore", perf.getPerformanceScore());
                    snapshot.put("assetConfidenceScore", perf.getConfidenceScore());
                    snapshot.put("assetImpressions", perf.getImpressions());
                    snapshot.put("assetClicks", perf.getClicks());
                    snapshot.put("assetConversions", perf.getConversions());
                    snapshot.put("assetSpend", perf.getSpend());
                    snapshot.put("assetRoas", perf.getRoas());
                });
            } catch (Exception e) {
                log.warn("Failed to get asset performance for baseline: {}", e.getMessage());
            }
        }

        snapshot.put("capturedAt", Instant.now().toString());
        return snapshot;
    }

    // ─── Delta Computation ──────────────────────────────────────────────────

    private Map<String, Object> computeDeltas(Map<String, Object> baseline, Map<String, Object> current) {
        Map<String, Object> deltas = new LinkedHashMap<>();
        Set<String> keys = Set.of("totalSpend", "totalRevenue", "totalImpressions", "totalClicks",
                "totalConversions", "overallRoas", "assetPerformanceScore", "assetRoas",
                "assetImpressions", "assetClicks", "assetConversions");

        for (String key : keys) {
            if (!baseline.containsKey(key) && !current.containsKey(key)) continue;
            BigDecimal base = toBd(baseline.get(key));
            BigDecimal curr = toBd(current.get(key));
            BigDecimal delta = curr.subtract(base);
            BigDecimal pct = base.compareTo(BigDecimal.ZERO) != 0
                    ? delta.divide(base, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : (curr.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);

            deltas.put(key, Map.of("baseline", base, "current", curr, "delta", delta, "percentChange", pct));
        }

        // Classification change
        String baseCls = (String) baseline.get("assetClassification");
        String currCls = (String) current.get("assetClassification");
        if (baseCls != null || currCls != null) {
            deltas.put("classificationChange", Map.of(
                    "before", baseCls != null ? baseCls : "N/A",
                    "after", currCls != null ? currCls : "N/A",
                    "changed", !Objects.equals(baseCls, currCls)
            ));
        }

        return deltas;
    }

    // ─── Impact Score ───────────────────────────────────────────────────────

    /**
     * Compute a normalized impact score (-1 to +1) based on metric deltas.
     * Weights vary by recommendation type:
     *   SCALE/DUPLICATE_WINNER → ROAS + conversions matter most
     *   STOP → spend reduction matters most
     *   TEST_MORE → data volume (impressions) matters
     */
    public double computeImpactScore(Map<String, Object> deltas, String recType) {
        double score = 0;

        double roasDelta = extractPctChange(deltas, "overallRoas");
        double convDelta = extractPctChange(deltas, "totalConversions");
        double spendDelta = extractPctChange(deltas, "totalSpend");
        double assetRoasDelta = extractPctChange(deltas, "assetRoas");
        double impressionDelta = extractPctChange(deltas, "totalImpressions");

        switch (recType) {
            case "SCALE", "DUPLICATE_WINNER" -> {
                score = (roasDelta * 0.30 + assetRoasDelta * 0.25 + convDelta * 0.30 + impressionDelta * 0.15) / 100;
            }
            case "STOP" -> {
                // For STOP, we want spend to decrease and overall ROAS to improve
                score = (roasDelta * 0.40 + convDelta * 0.10 - spendDelta * 0.30 + assetRoasDelta * 0.20) / 100;
            }
            case "TEST_MORE" -> {
                score = (impressionDelta * 0.30 + convDelta * 0.25 + roasDelta * 0.25 + assetRoasDelta * 0.20) / 100;
            }
            case "ADAPT_FOR_PLATFORM" -> {
                score = (assetRoasDelta * 0.35 + convDelta * 0.30 + impressionDelta * 0.20 + roasDelta * 0.15) / 100;
            }
            default -> {
                score = (roasDelta * 0.30 + convDelta * 0.30 + assetRoasDelta * 0.20 + impressionDelta * 0.20) / 100;
            }
        }

        return Math.max(-1.0, Math.min(1.0, score));
    }

    public String deriveVerdict(double impactScore) {
        if (impactScore >= 0.05) return "POSITIVE";
        if (impactScore <= -0.05) return "NEGATIVE";
        return "NEUTRAL";
    }

    private String buildOutcomeNotes(Map<String, Object> deltas, double impactScore, CreativeOptimizationRecommendation rec) {
        StringBuilder sb = new StringBuilder();
        sb.append("Impact score: ").append(String.format("%.3f", impactScore)).append(". ");

        double roasDelta = extractPctChange(deltas, "overallRoas");
        double convDelta = extractPctChange(deltas, "totalConversions");

        if (roasDelta > 5) sb.append("ROAS improved ").append(String.format("%.1f%%", roasDelta)).append(". ");
        else if (roasDelta < -5) sb.append("ROAS declined ").append(String.format("%.1f%%", Math.abs(roasDelta))).append(". ");

        if (convDelta > 10) sb.append("Conversions up ").append(String.format("%.1f%%", convDelta)).append(". ");
        else if (convDelta < -10) sb.append("Conversions down ").append(String.format("%.1f%%", Math.abs(convDelta))).append(". ");

        if (rec != null) sb.append("Recommendation type: ").append(rec.getRecommendationType()).append(".");

        return sb.toString().trim();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private double extractPctChange(Map<String, Object> deltas, String key) {
        Object entry = deltas.get(key);
        if (entry instanceof Map<?, ?> map) {
            Object pct = map.get("percentChange");
            if (pct instanceof BigDecimal bd) return bd.doubleValue();
            if (pct instanceof Number n) return n.doubleValue();
        }
        return 0;
    }

    Map<String, Object> outcomeToMap(RecommendationOutcome o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("recommendationId", o.getRecommendationId());
        m.put("businessId", o.getBusinessId());
        m.put("actionTaken", o.getActionTaken());
        m.put("actionDate", o.getActionDate());
        m.put("evaluationWindowDays", o.getEvaluationWindowDays());
        m.put("outcomeVerdict", o.getOutcomeVerdict());
        m.put("impactScore", o.getImpactScore());
        m.put("evaluationDate", o.getEvaluationDate());
        m.put("notes", o.getNotes());
        m.put("baselineSnapshot", parseJsonSafe(o.getBaselineSnapshot()));
        m.put("outcomeSnapshot", o.getOutcomeSnapshot() != null ? parseJsonSafe(o.getOutcomeSnapshot()) : null);
        m.put("deltaJson", o.getDeltaJson() != null ? parseJsonSafe(o.getDeltaJson()) : null);
        m.put("createdAt", o.getCreatedAt());
        m.put("updatedAt", o.getUpdatedAt());
        return m;
    }

    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return om.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", json);
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
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
