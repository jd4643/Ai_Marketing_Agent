package com.marketing.analytics.api;

import com.marketing.analytics.model.CreativeAssetPerformance;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/creative-assets")
public class CreativeAssetPerformanceController {

    private final CreativeAssetPerformanceRepository repo;

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

    public CreativeAssetPerformanceController(CreativeAssetPerformanceRepository repo) {
        this.repo = repo;
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
        repo.save(m);
        return Map.of("status", "OK", "id", m.getId());
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
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repo.findWinners(businessId, since, winnerMinImpressions, limit);
        List<Map<String, Object>> winners = new ArrayList<>();
        for (Object[] r : rows) {
            long impressions = ((Number) r[2]).longValue();
            long clicks = ((Number) r[3]).longValue();
            long conversions = ((Number) r[4]).longValue();
            double avgRoas = ((Number) r[7]).doubleValue();
            String status = classifyAsset(impressions, clicks, conversions, avgRoas);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("creativeAssetId", r[0]);
            item.put("platform", r[1]);
            item.put("impressions", impressions);
            item.put("clicks", clicks);
            item.put("conversions", conversions);
            item.put("spend", r[5]);
            item.put("revenue", r[6]);
            item.put("avgRoas", r[7]);
            item.put("metadata", r[8]);
            item.put("assetType", r[9]);
            item.put("promptText", r[10]);
            item.put("winnerStatus", status);
            winners.add(item);
        }
        return Map.of(
                "businessId", businessId,
                "days", days,
                "thresholds", Map.of(
                        "minImpressions", winnerMinImpressions,
                        "minClicks", winnerMinClicks,
                        "minConversions", winnerMinConversions,
                        "minRoas", winnerMinRoas,
                        "weakMaxRoas", weakMaxRoas),
                "winners", winners);
    }

    // ─── Insights ───────────────────────────────────────────────────────

    @GetMapping("/insights")
    public Map<String, Object> insights(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repo.findWinners(businessId, since, 0, 100);

        int total = rows.size();
        int winnerCount = 0, testingCount = 0, weakCount = 0, insufficientCount = 0;
        double bestRoas = 0;
        String bestAssetId = null;
        double totalSpend = 0, totalRevenue = 0;

        for (Object[] r : rows) {
            long impressions = ((Number) r[2]).longValue();
            long clicks = ((Number) r[3]).longValue();
            long conversions = ((Number) r[4]).longValue();
            double spend = ((Number) r[5]).doubleValue();
            double revenue = ((Number) r[6]).doubleValue();
            double avgRoas = ((Number) r[7]).doubleValue();
            totalSpend += spend;
            totalRevenue += revenue;

            String status = classifyAsset(impressions, clicks, conversions, avgRoas);
            switch (status) {
                case "WINNER" -> winnerCount++;
                case "TESTING" -> testingCount++;
                case "WEAK" -> weakCount++;
                default -> insufficientCount++;
            }
            if (avgRoas > bestRoas) {
                bestRoas = avgRoas;
                bestAssetId = r[0] != null ? r[0].toString() : null;
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

    // ─── Classification ─────────────────────────────────────────────────

    String classifyAsset(long impressions, long clicks, long conversions, double avgRoas) {
        if (impressions < winnerMinImpressions) return "INSUFFICIENT_DATA";
        if (avgRoas >= winnerMinRoas && clicks >= winnerMinClicks && conversions >= winnerMinConversions) return "WINNER";
        if (avgRoas <= weakMaxRoas) return "WEAK";
        return "TESTING";
    }
}
