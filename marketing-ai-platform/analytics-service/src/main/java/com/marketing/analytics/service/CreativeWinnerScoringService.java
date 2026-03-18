package com.marketing.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.CreativeAssetPerformance;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Deterministic winner detection engine that computes performance scores,
 * classifications, confidence scores, and reasoning for each creative asset.
 *
 * <p>Classification rules:
 * <ul>
 *   <li>INSUFFICIENT_DATA: impressions < minImpressions</li>
 *   <li>WINNER: roas >= minRoas AND clicks >= minClicks AND conversions >= minConversions</li>
 *   <li>WEAK: roas <= weakMaxRoas (with sufficient data)</li>
 *   <li>TESTING: everything else</li>
 * </ul>
 *
 * <p>Performance score: weighted composite of normalized CTR, ROAS, conversions, with CPA penalty.
 * <p>Confidence score: based on volume sufficiency and signal consistency.
 */
@Service
public class CreativeWinnerScoringService {

    private static final Logger log = LoggerFactory.getLogger(CreativeWinnerScoringService.class);
    private final CreativeAssetPerformanceRepository perfRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${creative.winner.min-impressions:3000}")
    long minImpressions;
    @Value("${creative.winner.min-clicks:100}")
    long minClicks;
    @Value("${creative.winner.min-conversions:3}")
    long minConversions;
    @Value("${creative.winner.min-roas:2.0}")
    double minRoas;
    @Value("${creative.weak.max-roas:0.8}")
    double weakMaxRoas;
    @Value("${creative.min-confidence:0.60}")
    double minConfidence;

    public CreativeWinnerScoringService(CreativeAssetPerformanceRepository perfRepo) {
        this.perfRepo = perfRepo;
    }

    /**
     * Result of scoring a single creative asset's aggregated performance.
     */
    public record ScoringResult(
            UUID creativeAssetId,
            String platform,
            String classification,
            BigDecimal performanceScore,
            BigDecimal confidenceScore,
            Map<String, Object> reasoning,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue,
            double avgRoas,
            double avgCtr,
            double avgCpc,
            double avgCpa,
            String metadataJson,
            String assetType,
            String promptText
    ) {}

    /**
     * Classify a single asset given aggregated metrics.
     */
    public String classify(long impressions, long clicks, long conversions, double avgRoas) {
        if (impressions < minImpressions) return "INSUFFICIENT_DATA";
        if (avgRoas >= minRoas && clicks >= minClicks && conversions >= minConversions) return "WINNER";
        if (avgRoas <= weakMaxRoas) return "WEAK";
        return "TESTING";
    }

    /**
     * Compute a deterministic performance score in [0, 100].
     *
     * <p>Components (weights):
     * <ul>
     *   <li>CTR contribution (25%): normalized against 3% as ceiling</li>
     *   <li>ROAS contribution (35%): normalized against 5.0 as ceiling</li>
     *   <li>Conversions contribution (25%): normalized against 50 as ceiling</li>
     *   <li>CPA penalty (15%): lower CPA is better; normalized against spend/impressions baseline</li>
     * </ul>
     */
    public BigDecimal computePerformanceScore(long impressions, long clicks, long conversions,
                                               double avgRoas, double avgCtr, double avgCpa,
                                               BigDecimal spend) {
        if (impressions == 0) return BigDecimal.ZERO;

        double ctrScore = Math.min(avgCtr / 0.03, 1.0) * 25.0;
        double roasScore = Math.min(avgRoas / 5.0, 1.0) * 35.0;
        double convScore = Math.min((double) conversions / 50.0, 1.0) * 25.0;

        double cpaPenalty = 0;
        if (avgCpa > 0 && spend != null && spend.doubleValue() > 0) {
            double baselineCpa = spend.doubleValue() / Math.max(impressions, 1) * 100;
            double cpaRatio = baselineCpa > 0 ? Math.min(baselineCpa / avgCpa, 1.0) : 0.5;
            cpaPenalty = cpaRatio * 15.0;
        } else {
            cpaPenalty = 7.5; // neutral when no CPA data
        }

        double total = ctrScore + roasScore + convScore + cpaPenalty;
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compute a confidence score in [0, 1].
     *
     * <p>Factors:
     * <ul>
     *   <li>Volume sufficiency: how close impressions/clicks/conversions are to thresholds</li>
     *   <li>Signal consistency: whether CTR, ROAS, conversions all point same direction</li>
     * </ul>
     */
    public BigDecimal computeConfidence(long impressions, long clicks, long conversions,
                                         double avgRoas, double avgCtr) {
        double volumeFactor = 0;
        volumeFactor += Math.min((double) impressions / (minImpressions * 3), 1.0) * 0.35;
        volumeFactor += Math.min((double) clicks / (minClicks * 3), 1.0) * 0.25;
        volumeFactor += Math.min((double) conversions / (minConversions * 5), 1.0) * 0.20;

        double consistencyFactor = 0;
        int positiveSignals = 0;
        int totalSignals = 0;
        if (impressions >= minImpressions) { positiveSignals++; totalSignals++; } else if (impressions > 0) { totalSignals++; }
        if (avgCtr >= 0.01) { positiveSignals++; totalSignals++; } else if (avgCtr > 0) { totalSignals++; }
        if (avgRoas >= minRoas) { positiveSignals++; totalSignals++; } else if (avgRoas > 0) { totalSignals++; }
        if (conversions >= minConversions) { positiveSignals++; totalSignals++; } else if (conversions > 0) { totalSignals++; }
        consistencyFactor = totalSignals > 0 ? (double) positiveSignals / totalSignals * 0.20 : 0;

        double total = volumeFactor + consistencyFactor;
        return BigDecimal.valueOf(Math.min(total, 1.0)).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Build explainable reasoning JSON for a classification.
     */
    public Map<String, Object> buildReasoning(long impressions, long clicks, long conversions,
                                               double avgRoas, String classification) {
        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("impressionsCheck", impressions >= minImpressions ? "passed" : "below_threshold");
        reasoning.put("impressionsValue", impressions);
        reasoning.put("impressionsThreshold", minImpressions);
        reasoning.put("clicksCheck", clicks >= minClicks ? "passed" : "below_threshold");
        reasoning.put("clicksValue", clicks);
        reasoning.put("clicksThreshold", minClicks);
        reasoning.put("conversionsCheck", conversions >= minConversions ? "passed" : "below_threshold");
        reasoning.put("conversionsValue", conversions);
        reasoning.put("conversionsThreshold", minConversions);
        reasoning.put("roasCheck", avgRoas >= minRoas ? "passed" : avgRoas <= weakMaxRoas ? "weak" : "below_threshold");
        reasoning.put("roasValue", BigDecimal.valueOf(avgRoas).setScale(4, RoundingMode.HALF_UP));
        reasoning.put("roasWinnerThreshold", minRoas);
        reasoning.put("roasWeakThreshold", weakMaxRoas);

        String summary = switch (classification) {
            case "WINNER" -> "High ROAS and sufficient volume — this asset is a proven performer.";
            case "WEAK" -> "Low ROAS with sufficient data — this asset is underperforming.";
            case "TESTING" -> "Metrics are between weak and winner thresholds — still gathering signal.";
            case "INSUFFICIENT_DATA" -> "Not enough data to classify — needs more impressions.";
            default -> "Unknown classification.";
        };
        reasoning.put("summary", summary);
        reasoning.put("classification", classification);
        return reasoning;
    }

    /**
     * Score and classify all assets for a business within a time window.
     * Returns a list of ScoringResult with full metrics.
     */
    public List<ScoringResult> scoreAllAssets(UUID businessId, int days, int limit) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = perfRepo.aggregateAllAssets(businessId, since, limit);
        List<ScoringResult> results = new ArrayList<>();

        for (Object[] r : rows) {
            UUID assetId = (UUID) r[0];
            String platform = (String) r[1];
            long impressions = ((Number) r[2]).longValue();
            long clicks = ((Number) r[3]).longValue();
            long conversions = ((Number) r[4]).longValue();
            BigDecimal spend = toBd(r[5]);
            BigDecimal revenue = toBd(r[6]);
            double avgRoas = ((Number) r[7]).doubleValue();
            double avgCtr = ((Number) r[8]).doubleValue();
            double avgCpc = ((Number) r[9]).doubleValue();
            double avgCpa = ((Number) r[10]).doubleValue();
            String metadataJson = r[11] != null ? r[11].toString() : null;
            String assetType = r[12] != null ? r[12].toString() : null;
            String promptText = r[13] != null ? r[13].toString() : null;

            String classification = classify(impressions, clicks, conversions, avgRoas);
            BigDecimal perfScore = computePerformanceScore(impressions, clicks, conversions, avgRoas, avgCtr, avgCpa, spend);
            BigDecimal confidence = computeConfidence(impressions, clicks, conversions, avgRoas, avgCtr);
            Map<String, Object> reasoning = buildReasoning(impressions, clicks, conversions, avgRoas, classification);

            results.add(new ScoringResult(assetId, platform, classification, perfScore, confidence,
                    reasoning, impressions, clicks, conversions, spend, revenue, avgRoas, avgCtr,
                    avgCpc, avgCpa, metadataJson, assetType, promptText));
        }
        return results;
    }

    /**
     * Recompute and persist classification for all performance rows of a business.
     */
    public int recomputeClassifications(UUID businessId, int days) {
        String requestId = MDC.get("requestId");
        log.info("Recomputing classifications for businessId={} days={} requestId={}", businessId, days, requestId);
        List<ScoringResult> scored = scoreAllAssets(businessId, days, 500);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<CreativeAssetPerformance> rows = perfRepo.findByBusinessIdAndRecordedAtAfter(businessId, since);

        Map<UUID, ScoringResult> scoreMap = new HashMap<>();
        for (ScoringResult s : scored) {
            scoreMap.put(s.creativeAssetId(), s);
        }

        int updated = 0;
        for (CreativeAssetPerformance row : rows) {
            ScoringResult sr = scoreMap.get(row.getCreativeAssetId());
            if (sr == null) continue;
            row.setClassification(sr.classification());
            row.setPerformanceScore(sr.performanceScore());
            row.setConfidenceScore(sr.confidenceScore());
            try {
                row.setReasoningJson(om.writeValueAsString(sr.reasoning()));
            } catch (Exception e) {
                row.setReasoningJson(null);
            }
            row.setUpdatedAt(Instant.now());
            perfRepo.save(row);
            updated++;
        }
        log.info("Recomputed {} performance rows for businessId={}", updated, businessId);
        return updated;
    }

    private BigDecimal toBd(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
