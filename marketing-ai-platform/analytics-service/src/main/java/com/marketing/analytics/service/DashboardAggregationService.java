package com.marketing.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.dashboard.DashboardDtos.*;
import com.marketing.analytics.model.AdPlatformConnection;
import com.marketing.analytics.model.BusinessProfile;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.model.ExecutionPlan;
import com.marketing.analytics.repo.*;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class DashboardAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DashboardAggregationService.class);

    private final BusinessProfileRepository profileRepo;
    private final CampaignMetricRepository campaignRepo;
    private final CreativeWinnerScoringService scoringService;
    private final CreativeOptimizationRecommendationRepository recRepo;
    private final CreativeOptimizationRecommendationService recService;
    private final AdPlatformConnectionRepository connectionRepo;
    private final AdPlatformInsightRepository insightRepo;
    private final CreativeAssetPlatformMappingRepository mappingRepo;
    private final ExecutionPlanRepository planRepo;
    private final ObjectMapper om = new ObjectMapper();

    public DashboardAggregationService(
            BusinessProfileRepository profileRepo,
            CampaignMetricRepository campaignRepo,
            CreativeWinnerScoringService scoringService,
            CreativeOptimizationRecommendationRepository recRepo,
            CreativeOptimizationRecommendationService recService,
            AdPlatformConnectionRepository connectionRepo,
            AdPlatformInsightRepository insightRepo,
            CreativeAssetPlatformMappingRepository mappingRepo,
            ExecutionPlanRepository planRepo) {
        this.profileRepo = profileRepo;
        this.campaignRepo = campaignRepo;
        this.scoringService = scoringService;
        this.recRepo = recRepo;
        this.recService = recService;
        this.connectionRepo = connectionRepo;
        this.insightRepo = insightRepo;
        this.mappingRepo = mappingRepo;
        this.planRepo = planRepo;
    }

    // ─── Overview ───────────────────────────────────────────────────────

    public OverviewResponse getOverview(UUID businessId, int days) {
        log.info("Dashboard overview businessId={} days={} requestId={}", businessId, days, MDC.get("requestId"));

        BusinessProfile profile = profileRepo.findById(businessId).orElse(null);

        SpendSummary summary = buildSpendSummary(businessId, days);

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, 500);
        CreativeHealth health = computeHealth(scored);
        TopSignals signals = computeTopSignals(scored);

        int openRecs = recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, "OPEN").size();

        List<SyncStatus> syncStatuses = connectionRepo.findByBusinessId(businessId).stream()
                .map(c -> new SyncStatus(c.getPlatform(), c.getId(), c.getConnectionName(),
                        c.getStatus(), c.getLastSyncedAt()))
                .toList();

        return new OverviewResponse(
                businessId,
                profile != null ? profile.getBusinessName() : null,
                profile != null ? profile.getIndustry() : null,
                days, summary, health, openRecs, signals, syncStatuses);
    }

    // ─── Creatives ──────────────────────────────────────────────────────

    public CreativesResponse getCreatives(UUID businessId, String status, String platform, int limit) {
        log.info("Dashboard creatives businessId={} status={} platform={} limit={} requestId={}",
                businessId, status, platform, limit, MDC.get("requestId"));

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, 30, limit * 3);
        List<CreativeCard> cards = new ArrayList<>();

        for (ScoringResult sr : scored) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(sr.classification())) continue;
            if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase(sr.platform())) continue;
            if (cards.size() >= limit) break;

            String hook = extractField(sr.metadataJson(), "hook");
            cards.add(new CreativeCard(
                    sr.creativeAssetId(), sr.platform(), sr.assetType(),
                    sr.classification(), sr.performanceScore(), sr.confidenceScore(),
                    sr.impressions(), sr.clicks(), sr.conversions(),
                    sr.spend(), sr.revenue(), sr.avgRoas(), sr.avgCtr(),
                    hook, sr.promptText()));
        }

        return new CreativesResponse(businessId, cards.size(), cards);
    }

    // ─── Recommendations ────────────────────────────────────────────────

    public RecommendationsResponse getRecommendations(UUID businessId, String status) {
        log.info("Dashboard recommendations businessId={} status={} requestId={}",
                businessId, status, MDC.get("requestId"));

        List<CreativeOptimizationRecommendation> recs;
        if (status != null && !status.isBlank()) {
            recs = recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, status);
        } else {
            recs = recRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        }

        List<RecommendationCard> high = new ArrayList<>();
        List<RecommendationCard> medium = new ArrayList<>();
        List<RecommendationCard> low = new ArrayList<>();

        for (CreativeOptimizationRecommendation rec : recs) {
            RecommendationCard card = toRecommendationCard(rec);
            switch (rec.getPriority()) {
                case "HIGH" -> high.add(card);
                case "MEDIUM" -> medium.add(card);
                default -> low.add(card);
            }
        }

        return new RecommendationsResponse(businessId, recs.size(), high, medium, low);
    }

    // ─── Strategy ───────────────────────────────────────────────────────

    public StrategyResponse getStrategy(UUID businessId) {
        log.info("Dashboard strategy businessId={} requestId={}", businessId, MDC.get("requestId"));

        BusinessProfile profile = profileRepo.findById(businessId).orElse(null);

        SpendSummary perf = buildSpendSummary(businessId, 30);

        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, 30, 500);
        CreativeHealth health = computeHealth(scored);

        List<CreativeOptimizationRecommendation> openRecs =
                recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, "OPEN");
        List<RecommendationCard> topRecs = openRecs.stream()
                .filter(r -> "HIGH".equals(r.getPriority()))
                .limit(5)
                .map(this::toRecommendationCard)
                .toList();

        return new StrategyResponse(
                businessId,
                profile != null ? profile.getBusinessName() : null,
                profile != null ? profile.getIndustry() : null,
                profile != null ? profile.getTargetAudience() : null,
                perf, health, topRecs);
    }

    // ─── Platforms ──────────────────────────────────────────────────────

    public PlatformsResponse getPlatforms(UUID businessId, int days) {
        log.info("Dashboard platforms businessId={} days={} requestId={}", businessId, days, MDC.get("requestId"));

        LocalDate since = LocalDate.now().minusDays(days);
        List<AdPlatformConnection> connections = connectionRepo.findByBusinessId(businessId);
        List<PlatformCard> cards = new ArrayList<>();

        for (AdPlatformConnection conn : connections) {
            Object[] agg = insightRepo.aggregateByConnection(conn.getId(), since);

            BigDecimal totalSpend = BigDecimal.ZERO;
            long totalImpressions = 0, totalClicks = 0, totalConversions = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            long totalReach = 0;

            if (agg != null && agg.length >= 6 && agg[0] != null) {
                totalSpend = toBd(agg[0]);
                totalImpressions = ((Number) agg[1]).longValue();
                totalClicks = ((Number) agg[2]).longValue();
                totalConversions = ((Number) agg[3]).longValue();
                totalRevenue = toBd(agg[4]);
                totalReach = ((Number) agg[5]).longValue();
            }

            List<Object[]> campaignRows = insightRepo.topCampaigns(conn.getId(), since, 5);
            List<PlatformCampaign> topCampaigns = campaignRows.stream()
                    .map(r -> new PlatformCampaign(
                            str(r[0]), str(r[1]),
                            toBd(r[2]),
                            ((Number) r[3]).longValue(),
                            ((Number) r[4]).longValue(),
                            ((Number) r[5]).longValue()))
                    .toList();

            List<Object[]> adRows = insightRepo.topAds(conn.getId(), since, 5);
            List<PlatformAd> topAds = adRows.stream()
                    .map(r -> new PlatformAd(
                            str(r[0]), str(r[1]),
                            toBd(r[2]),
                            ((Number) r[3]).longValue(),
                            ((Number) r[4]).longValue(),
                            ((Number) r[5]).longValue(),
                            ((Number) r[6]).doubleValue()))
                    .toList();

            long mappedAssets = mappingRepo.countByConnectionId(conn.getId());

            cards.add(new PlatformCard(
                    conn.getPlatform(), conn.getId(), conn.getConnectionName(),
                    conn.getStatus(), conn.getLastSyncedAt(),
                    totalSpend, totalImpressions, totalClicks, totalConversions,
                    totalRevenue, totalReach, mappedAssets, topCampaigns, topAds));
        }

        return new PlatformsResponse(businessId, days, cards);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private SpendSummary buildSpendSummary(UUID businessId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = campaignRepo.aggregate(businessId, since);
        if (rows.isEmpty()) return SpendSummary.EMPTY;

        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalImpressions = 0, totalClicks = 0, totalConversions = 0;

        for (Object[] r : rows) {
            totalSpend = totalSpend.add(toBd(r[1]));
            totalImpressions += ((Number) r[2]).longValue();
            totalClicks += ((Number) r[3]).longValue();
            totalConversions += ((Number) r[4]).longValue();
            totalRevenue = totalRevenue.add(toBd(r[5]));
        }

        BigDecimal roas = totalSpend.signum() > 0
                ? totalRevenue.divide(totalSpend, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SpendSummary(
                totalSpend.setScale(2, RoundingMode.HALF_UP),
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                roas, totalImpressions, totalClicks, totalConversions);
    }

    private CreativeHealth computeHealth(List<ScoringResult> scored) {
        int winners = 0, testing = 0, weak = 0, insufficient = 0;
        for (ScoringResult sr : scored) {
            switch (sr.classification()) {
                case "WINNER" -> winners++;
                case "TESTING" -> testing++;
                case "WEAK" -> weak++;
                default -> insufficient++;
            }
        }
        return new CreativeHealth(scored.size(), winners, testing, weak, insufficient);
    }

    private TopSignals computeTopSignals(List<ScoringResult> scored) {
        if (scored.isEmpty()) return TopSignals.EMPTY;

        double bestRoas = 0;
        String bestPlatform = null;
        String bestAssetType = null;
        String topHook = null;

        Map<String, Double> platformRoas = new HashMap<>();
        Map<String, Integer> platformCount = new HashMap<>();

        for (ScoringResult sr : scored) {
            platformRoas.merge(sr.platform(), sr.avgRoas(), Double::sum);
            platformCount.merge(sr.platform(), 1, Integer::sum);

            if (sr.avgRoas() > bestRoas) {
                bestRoas = sr.avgRoas();
                bestAssetType = sr.assetType();
                topHook = extractField(sr.metadataJson(), "hook");
            }
        }

        for (Map.Entry<String, Double> e : platformRoas.entrySet()) {
            double avg = e.getValue() / platformCount.getOrDefault(e.getKey(), 1);
            if (bestPlatform == null ||
                    avg > platformRoas.getOrDefault(bestPlatform, 0.0) / platformCount.getOrDefault(bestPlatform, 1)) {
                bestPlatform = e.getKey();
            }
        }

        return new TopSignals(bestPlatform, bestAssetType, topHook);
    }

    // ─── Execution Overview ────────────────────────────────────────────

    public ExecutionOverview getExecutionOverview(UUID businessId) {
        log.info("Dashboard execution overview businessId={} requestId={}", businessId, MDC.get("requestId"));

        List<ExecutionPlan> allPlans = planRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        if (allPlans.isEmpty()) {
            return ExecutionOverview.EMPTY;
        }

        long active = allPlans.stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()) || "IN_PROGRESS".equals(p.getStatus()))
                .count();

        List<ExecutionPlanSummary> recent = allPlans.stream()
                .limit(5)
                .map(p -> new ExecutionPlanSummary(
                        p.getId(), p.getName(), p.getStatus(),
                        p.getTotalTasks(), p.getCompletedTasks(),
                        p.getFailedTasks(), p.getSkippedTasks(),
                        p.getTotalTasks() > 0
                                ? Math.round((float) (p.getCompletedTasks() + p.getSkippedTasks()) / p.getTotalTasks() * 100)
                                : 0,
                        p.getStartedAt(), p.getCreatedAt()))
                .toList();

        return new ExecutionOverview((int) active, allPlans.size(), recent);
    }

    // ─── Private Helpers ────────────────────────────────────────────────

    private RecommendationCard toRecommendationCard(CreativeOptimizationRecommendation rec) {
        return new RecommendationCard(
                rec.getId(),
                rec.getTitle(),
                rec.getRecommendationType(),
                rec.getPriority(),
                rec.getStatus(),
                rec.getDescription(),
                rec.getCreativeAssetId(),
                rec.getSuggestedNextAction(),
                recService.availableActions(rec),
                rec.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private String extractField(String json, String field) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = om.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object v = map.get(field);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBd(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private String str(Object val) {
        return val != null ? val.toString() : null;
    }
}
