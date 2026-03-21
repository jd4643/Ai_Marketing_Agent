package com.marketing.analytics.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    // ─── Shared sub-records ─────────────────────────────────────────────

    public record SpendSummary(
            BigDecimal totalSpend,
            BigDecimal totalRevenue,
            BigDecimal overallRoas,
            long totalImpressions,
            long totalClicks,
            long totalConversions
    ) {
        public static final SpendSummary EMPTY = new SpendSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
    }

    public record CreativeHealth(
            int totalAssets,
            int winners,
            int testing,
            int weak,
            int insufficientData
    ) {
        public static final CreativeHealth EMPTY = new CreativeHealth(0, 0, 0, 0, 0);
    }

    public record TopSignals(
            String bestPlatform,
            String bestAssetType,
            String topHook
    ) {
        public static final TopSignals EMPTY = new TopSignals(null, null, null);
    }

    public record SyncStatus(
            String platform,
            UUID connectionId,
            String connectionName,
            String status,
            Instant lastSyncedAt
    ) {}

    // ─── Overview ───────────────────────────────────────────────────────

    public record OverviewResponse(
            UUID businessId,
            String businessName,
            String industry,
            int days,
            SpendSummary summary,
            CreativeHealth creativeHealth,
            int openRecommendations,
            TopSignals topSignals,
            List<SyncStatus> syncStatus
    ) {}

    // ─── Creatives ──────────────────────────────────────────────────────

    public record CreativeCard(
            UUID creativeAssetId,
            String platform,
            String assetType,
            String classification,
            BigDecimal performanceScore,
            BigDecimal confidenceScore,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal spend,
            BigDecimal revenue,
            double avgRoas,
            double avgCtr,
            String hook,
            String promptText
    ) {}

    public record CreativesResponse(
            UUID businessId,
            int total,
            List<CreativeCard> creatives
    ) {}

    // ─── Recommendations ────────────────────────────────────────────────

    public record RecommendationCard(
            UUID recommendationId,
            String title,
            String type,
            String priority,
            String status,
            String description,
            UUID relatedAssetId,
            String suggestedNextAction,
            List<String> availableActions,
            Instant createdAt
    ) {}

    public record RecommendationsResponse(
            UUID businessId,
            int total,
            List<RecommendationCard> highPriority,
            List<RecommendationCard> mediumPriority,
            List<RecommendationCard> lowPriority
    ) {}

    // ─── Strategy ───────────────────────────────────────────────────────

    public record StrategyResponse(
            UUID businessId,
            String businessName,
            String industry,
            String targetAudience,
            SpendSummary campaignPerformance,
            CreativeHealth creativeHealth,
            List<RecommendationCard> topRecommendations
    ) {}

    // ─── Platforms ──────────────────────────────────────────────────────

    public record PlatformCampaign(
            String externalCampaignId,
            String campaignName,
            BigDecimal spend,
            long impressions,
            long clicks,
            long conversions
    ) {}

    public record PlatformAd(
            String externalAdId,
            String adName,
            BigDecimal spend,
            long impressions,
            long clicks,
            long conversions,
            double avgRoas
    ) {}

    public record PlatformCard(
            String platform,
            UUID connectionId,
            String connectionName,
            String status,
            Instant lastSyncedAt,
            BigDecimal totalSpend,
            long totalImpressions,
            long totalClicks,
            long totalConversions,
            BigDecimal totalRevenue,
            long totalReach,
            long mappedAssets,
            List<PlatformCampaign> topCampaigns,
            List<PlatformAd> topAds
    ) {}

    public record PlatformsResponse(
            UUID businessId,
            int days,
            List<PlatformCard> platforms
    ) {}

    // ─── Execution ──────────────────────────────────────────────────────

    public record ExecutionPlanSummary(
            UUID planId,
            String name,
            String status,
            int totalTasks,
            int completedTasks,
            int failedTasks,
            int skippedTasks,
            int progressPercent,
            Instant startedAt,
            Instant createdAt
    ) {}

    public record ExecutionOverview(
            int activePlans,
            int totalPlans,
            List<ExecutionPlanSummary> recentPlans
    ) {
        public static final ExecutionOverview EMPTY = new ExecutionOverview(0, 0, List.of());
    }
}
