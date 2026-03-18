package com.marketing.analytics.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.CreativeAssetPerformance;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService.RecommendationResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/creative-assets")
public class CreativeAssetPerformanceController {

    private static final Logger log = LoggerFactory.getLogger(CreativeAssetPerformanceController.class);
    private final CreativeAssetPerformanceRepository repo;
    private final CreativeWinnerScoringService scoringService;
    private final CreativeOptimizationRecommendationService recommendationService;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${creative.winner.min-impressions:3000}")
    long winnerMinImpressions;
    @Value("${creative.winner.min-clicks:100}")
    long winnerMinClicks;
    @Value("${creative.winner.min-conversions:3}")
    long winnerMinConversions;
    @Value("${creative.winner.min-roas:2.0}")
    double winnerMinRoas;
    @Value("${creative.weak.max-roas:0.8}")
    double weakMaxRoas;

    public CreativeAssetPerformanceController(CreativeAssetPerformanceRepository repo,
                                              CreativeWinnerScoringService scoringService,
                                              CreativeOptimizationRecommendationService recommendationService) {
        this.repo = repo;
        this.scoringService = scoringService;
        this.recommendationService = recommendationService;
    }

    // ─── Ingest ─────────────────────────────────────────────────────────

    public record IngestRequest(
            @NotNull UUID creativeAssetId,
            @NotNull UUID businessId,
            @NotBlank String platform,
            Long impressions,
            Long clicks,
            Long conversions,
            BigDecimal spend,
            BigDecimal revenue,
            BigDecimal ctr,
            BigDecimal cpc,
            BigDecimal cpa,
            BigDecimal roas,
            @NotNull Instant recordedAt) {}

    @PostMapping("/metrics/ingest")
    public Map<String, Object> ingest(@Valid @RequestBody IngestRequest r) {
        CreativeAssetPerformance m = new CreativeAssetPerformance();
        m.setId(UUID.randomUUID());
        m.setCreativeAssetId(r.creativeAssetId());
        m.setBusinessId(r.businessId());
        m.setPlatform(r.platform());
        m.setImpressions(r.impressions());
        m.setClicks(r.clicks());
        m.setConversions(r.conversions());
        m.setSpend(r.spend());
        m.setRevenue(r.revenue());
        m.setCtr(r.ctr());
        m.setCpc(r.cpc());
        m.setCpa(r.cpa());
        m.setRoas(r.roas());
        m.setRecordedAt(r.recordedAt());
        m.setCreatedAt(Instant.now());

        // Compute classification on ingest
        long imp = r.impressions() != null ? r.impressions() : 0;
        long clk = r.clicks() != null ? r.clicks() : 0;
        long conv = r.conversions() != null ? r.conversions() : 0;
        double avgRoas = r.roas() != null ? r.roas().doubleValue() : 0;
        double avgCtr = r.ctr() != null ? r.ctr().doubleValue() : 0;
        double avgCpa = r.cpa() != null ? r.cpa().doubleValue() : 0;

        m.setClassification(scoringService.classify(imp, clk, conv, avgRoas));
        m.setPerformanceScore(scoringService.computePerformanceScore(imp, clk, conv, avgRoas, avgCtr, avgCpa, r.spend()));
        m.setConfidenceScore(scoringService.computeConfidence(imp, clk, conv, avgRoas, avgCtr));
        try {
            m.setReasoningJson(om.writeValueAsString(scoringService.buildReasoning(imp, clk, conv, avgRoas, m.getClassification())));
        } catch (Exception e) {
            log.warn("Failed to serialize reasoning: {}", e.getMessage());
        }
        m.setUpdatedAt(Instant.now());

        repo.save(m);
        return Map.of("status", "OK", "id", m.getId(), "classification", m.getClassification());
    }

    // ─── Summary ────────────────────────────────────────────────────────

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repo.aggregateByAsset(businessId, since);
        List<Map<String, Object>> items = rows.stream().map(r -> Map.<String, Object>of(
                "creativeAssetId", r[0],
                "platform", r[1],
                "impressions", r[2],
                "clicks", r[3],
                "conversions", r[4],
                "spend", r[5],
                "revenue", r[6],
                "avgRoas", r[7]
        )).toList();
        return Map.of("businessId", businessId, "days", days, "assets", items);
    }

    // ─── Winners ────────────────────────────────────────────────────────

    @GetMapping("/winners")
    public Map<String, Object> winners(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "meta") String platform,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, limit * 3);
        List<Map<String, Object>> winners = new ArrayList<>();
        for (ScoringResult sr : scored) {
            if (!"WINNER".equals(sr.classification())) continue;
            if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase(sr.platform())) continue;
            if (winners.size() >= limit) break;
            winners.add(buildAssetResponse(sr));
        }
        return Map.of(
                "businessId", businessId,
                "days", days,
                "platform", platform,
                "thresholds", Map.of(
                        "minImpressions", winnerMinImpressions,
                        "minClicks", winnerMinClicks,
                        "minConversions", winnerMinConversions,
                        "minRoas", winnerMinRoas,
                        "weakMaxRoas", weakMaxRoas),
                "winners", winners);
    }

    // ─── Losers ─────────────────────────────────────────────────────────

    @GetMapping("/losers")
    public Map<String, Object> losers(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "meta") String platform,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, limit * 3);
        List<Map<String, Object>> weak = new ArrayList<>();
        for (ScoringResult sr : scored) {
            if (!"WEAK".equals(sr.classification())) continue;
            if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase(sr.platform())) continue;
            if (weak.size() >= limit) break;
            weak.add(buildAssetResponse(sr));
        }
        return Map.of("businessId", businessId, "days", days, "platform", platform, "losers", weak);
    }

    // ─── Testing ────────────────────────────────────────────────────────

    @GetMapping("/testing")
    public Map<String, Object> testing(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "meta") String platform,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, limit * 3);
        List<Map<String, Object>> testingAssets = new ArrayList<>();
        for (ScoringResult sr : scored) {
            if (!"TESTING".equals(sr.classification())) continue;
            if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase(sr.platform())) continue;
            if (testingAssets.size() >= limit) break;
            testingAssets.add(buildAssetResponse(sr));
        }
        return Map.of("businessId", businessId, "days", days, "platform", platform, "testing", testingAssets);
    }

    // ─── Scorecard ──────────────────────────────────────────────────────

    @GetMapping("/scorecard")
    public Map<String, Object> scorecard(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, 500);

        int winnersCount = 0, weakCount = 0, testingCount = 0, insufficientCount = 0;
        double bestRoas = 0;
        String bestPlatform = null;
        String bestAssetType = null;
        String topHook = null;
        String topConcept = null;
        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        Map<String, Double> platformRoas = new HashMap<>();
        Map<String, Integer> platformCount = new HashMap<>();

        for (ScoringResult sr : scored) {
            switch (sr.classification()) {
                case "WINNER" -> winnersCount++;
                case "WEAK" -> weakCount++;
                case "TESTING" -> testingCount++;
                default -> insufficientCount++;
            }
            totalSpend = totalSpend.add(sr.spend() != null ? sr.spend() : BigDecimal.ZERO);
            totalRevenue = totalRevenue.add(sr.revenue() != null ? sr.revenue() : BigDecimal.ZERO);

            platformRoas.merge(sr.platform(), sr.avgRoas(), Double::sum);
            platformCount.merge(sr.platform(), 1, Integer::sum);

            if (sr.avgRoas() > bestRoas) {
                bestRoas = sr.avgRoas();
                bestAssetType = sr.assetType();
                topHook = extractField(sr.metadataJson(), "hook");
                topConcept = extractField(sr.metadataJson(), "conceptName");
            }
        }

        // Find best platform by average ROAS
        for (Map.Entry<String, Double> e : platformRoas.entrySet()) {
            double avg = e.getValue() / platformCount.getOrDefault(e.getKey(), 1);
            if (bestPlatform == null || avg > platformRoas.getOrDefault(bestPlatform, 0.0) / platformCount.getOrDefault(bestPlatform, 1)) {
                bestPlatform = e.getKey();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);
        result.put("days", days);
        result.put("totalAssets", scored.size());
        result.put("winners", winnersCount);
        result.put("weak", weakCount);
        result.put("testing", testingCount);
        result.put("insufficientData", insufficientCount);
        result.put("bestPlatform", bestPlatform);
        result.put("bestAssetType", bestAssetType);
        result.put("topHook", topHook);
        result.put("topConcept", topConcept);
        result.put("totalSpend", totalSpend.setScale(2, RoundingMode.HALF_UP));
        result.put("totalRevenue", totalRevenue.setScale(2, RoundingMode.HALF_UP));
        result.put("overallRoas", totalSpend.signum() > 0
                ? totalRevenue.divide(totalSpend, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return result;
    }

    // ─── Recommendations ────────────────────────────────────────────────

    @GetMapping("/recommendations")
    public Map<String, Object> recommendations(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        List<RecommendationResult> recs = recommendationService.generateAndPersist(businessId, days);
        List<Map<String, Object>> items = new ArrayList<>();
        for (RecommendationResult r : recs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recommendationType", r.recommendationType());
            item.put("priority", r.priority());
            item.put("title", r.title());
            item.put("description", r.description());
            item.put("relatedCreativeAssetId", r.relatedCreativeAssetId());
            item.put("metricsSummary", r.metricsSummary());
            item.put("reasoning", r.reasoning());
            item.put("suggestedNextAction", r.suggestedNextAction());
            items.add(item);
        }
        return Map.of("businessId", businessId, "days", days, "recommendations", items, "count", items.size());
    }

    // ─── Recompute Classifications ──────────────────────────────────────

    @PostMapping("/recompute")
    public Map<String, Object> recompute(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        int updated = scoringService.recomputeClassifications(businessId, days);
        return Map.of("businessId", businessId, "days", days, "rowsUpdated", updated);
    }

    // ─── Insights (backward-compatible, enhanced) ───────────────────────

    @GetMapping("/insights")
    public Map<String, Object> insights(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, 200);

        int total = scored.size();
        int winnerCount = 0, testingCount = 0, weakCount = 0, insufficientCount = 0;
        double bestRoas = 0;
        String bestAssetId = null;
        double totalSpend = 0, totalRevenue = 0;

        for (ScoringResult sr : scored) {
            totalSpend += sr.spend() != null ? sr.spend().doubleValue() : 0;
            totalRevenue += sr.revenue() != null ? sr.revenue().doubleValue() : 0;
            switch (sr.classification()) {
                case "WINNER" -> winnerCount++;
                case "TESTING" -> testingCount++;
                case "WEAK" -> weakCount++;
                default -> insufficientCount++;
            }
            if (sr.avgRoas() > bestRoas) {
                bestRoas = sr.avgRoas();
                bestAssetId = sr.creativeAssetId() != null ? sr.creativeAssetId().toString() : null;
            }
        }

        double overallRoas = totalSpend > 0 ? totalRevenue / totalSpend : 0;

        List<String> recommendations = new ArrayList<>();
        if (winnerCount > 0) recommendations.add("Scale budget on " + winnerCount + " winning asset(s). Create variations of winning concepts.");
        if (weakCount > 0) recommendations.add("Pause or rework " + weakCount + " weak asset(s) with ROAS < " + weakMaxRoas + ".");
        if (testingCount > 0) recommendations.add(testingCount + " asset(s) still in testing phase — let them run until they hit " + winnerMinImpressions + " impressions before judging.");
        if (total == 0) recommendations.add("No creative asset performance data yet. Ingest metrics to enable winner detection.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);
        result.put("days", days);
        result.put("totalAssets", total);
        result.put("breakdown", Map.of("winners", winnerCount, "testing", testingCount, "weak", weakCount, "insufficientData", insufficientCount));
        result.put("bestAssetId", bestAssetId);
        result.put("bestRoas", BigDecimal.valueOf(bestRoas).setScale(2, RoundingMode.HALF_UP));
        result.put("overallRoas", BigDecimal.valueOf(overallRoas).setScale(2, RoundingMode.HALF_UP));
        result.put("totalSpend", BigDecimal.valueOf(totalSpend).setScale(2, RoundingMode.HALF_UP));
        result.put("totalRevenue", BigDecimal.valueOf(totalRevenue).setScale(2, RoundingMode.HALF_UP));
        result.put("recommendations", recommendations);
        return result;
    }

    // ─── Classification (package-visible for backward compat with tests) ─

    String classifyAsset(long impressions, long clicks, long conversions, double avgRoas) {
        return scoringService.classify(impressions, clicks, conversions, avgRoas);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> buildAssetResponse(ScoringResult sr) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("creativeAssetId", sr.creativeAssetId());
        item.put("platform", sr.platform());
        item.put("classification", sr.classification());
        item.put("performanceScore", sr.performanceScore());
        item.put("confidenceScore", sr.confidenceScore());
        item.put("impressions", sr.impressions());
        item.put("clicks", sr.clicks());
        item.put("conversions", sr.conversions());
        item.put("spend", sr.spend());
        item.put("revenue", sr.revenue());
        item.put("avgRoas", BigDecimal.valueOf(sr.avgRoas()).setScale(4, RoundingMode.HALF_UP));
        item.put("avgCtr", BigDecimal.valueOf(sr.avgCtr()).setScale(6, RoundingMode.HALF_UP));
        item.put("assetType", sr.assetType());
        item.put("reasoning", sr.reasoning());
        // Extract metadata fields if available
        if (sr.metadataJson() != null) {
            try {
                Map<String, Object> meta = om.readValue(sr.metadataJson(), new TypeReference<>() {});
                item.put("metadata", meta);
                if (meta.get("hook") != null) item.put("hook", meta.get("hook"));
                if (meta.get("conceptName") != null) item.put("conceptName", meta.get("conceptName"));
                if (meta.get("headline") != null) item.put("headline", meta.get("headline"));
            } catch (Exception ignored) {}
        }
        return item;
    }

    private String extractField(String metadataJson, String field) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        try {
            Map<String, Object> meta = om.readValue(metadataJson, new TypeReference<>() {});
            Object val = meta.get(field);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
