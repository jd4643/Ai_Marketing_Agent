package com.marketing.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.marketing.analytics.dashboard.DashboardDtos.*;
import com.marketing.analytics.model.AdPlatformConnection;
import com.marketing.analytics.model.BusinessProfile;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.repo.*;
import com.marketing.analytics.service.*;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationServiceTest {

    @Mock BusinessProfileRepository profileRepo;
    @Mock CampaignMetricRepository campaignRepo;
    @Mock CreativeWinnerScoringService scoringService;
    @Mock CreativeOptimizationRecommendationRepository recRepo;
    @Mock CreativeOptimizationRecommendationService recService;
    @Mock AdPlatformConnectionRepository connectionRepo;
    @Mock AdPlatformInsightRepository insightRepo;
    @Mock CreativeAssetPlatformMappingRepository mappingRepo;
    @Mock ExecutionPlanRepository planRepo;
    @InjectMocks DashboardAggregationService service;

    static final UUID BIZ = UUID.randomUUID();

    // ─── Overview ───────────────────────────────────────────────────────

    @Test void overviewAggregatesAllSources() {
        BusinessProfile bp = new BusinessProfile();
        bp.setId(BIZ);
        bp.setBusinessName("Acme");
        bp.setIndustry("retail");
        when(profileRepo.findById(BIZ)).thenReturn(Optional.of(bp));

        when(campaignRepo.aggregate(eq(BIZ), any(Instant.class))).thenReturn(
                Collections.singletonList(new Object[]{"meta", bd(500), 25000L, 1000L, 50L, bd(2500), bd(5.0)}));

        UUID assetId = UUID.randomUUID();
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(500))).thenReturn(List.of(
                scoringResult(assetId, "meta", "WINNER", 5000, 200, 15, 500, 3000, 6.0, 0.04,
                        "{\"hook\":\"Save big\"}", "image")));

        when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ, "OPEN")).thenReturn(
                List.of(makeRec("SCALE", "HIGH")));

        AdPlatformConnection conn = makeConnection("META", "ACTIVE");
        when(connectionRepo.findByBusinessId(BIZ)).thenReturn(List.of(conn));

        OverviewResponse resp = service.getOverview(BIZ, 30);

        assertThat(resp.businessId()).isEqualTo(BIZ);
        assertThat(resp.businessName()).isEqualTo("Acme");
        assertThat(resp.summary().totalSpend()).isEqualByComparingTo(bd(500));
        assertThat(resp.creativeHealth().winners()).isEqualTo(1);
        assertThat(resp.openRecommendations()).isEqualTo(1);
        assertThat(resp.topSignals().bestPlatform()).isEqualTo("meta");
        assertThat(resp.topSignals().topHook()).isEqualTo("Save big");
        assertThat(resp.syncStatus()).hasSize(1);
        assertThat(resp.syncStatus().get(0).platform()).isEqualTo("META");
    }

    @Test void overviewEmptyState() {
        when(profileRepo.findById(BIZ)).thenReturn(Optional.empty());
        when(campaignRepo.aggregate(eq(BIZ), any(Instant.class))).thenReturn(List.of());
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(500))).thenReturn(List.of());
        when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ, "OPEN")).thenReturn(List.of());
        when(connectionRepo.findByBusinessId(BIZ)).thenReturn(List.of());

        OverviewResponse resp = service.getOverview(BIZ, 30);

        assertThat(resp.businessName()).isNull();
        assertThat(resp.summary()).isEqualTo(SpendSummary.EMPTY);
        assertThat(resp.creativeHealth()).isEqualTo(CreativeHealth.EMPTY);
        assertThat(resp.openRecommendations()).isZero();
        assertThat(resp.topSignals()).isEqualTo(TopSignals.EMPTY);
        assertThat(resp.syncStatus()).isEmpty();
    }

    // ─── Creatives ──────────────────────────────────────────────────────

    @Test void creativesReturnsFilteredCards() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(150))).thenReturn(List.of(
                scoringResult(a1, "meta", "WINNER", 5000, 200, 15, 500, 3000, 6.0, 0.04, null, "image"),
                scoringResult(a2, "google", "WEAK", 3000, 50, 1, 400, 100, 0.25, 0.017, null, "video")));

        CreativesResponse resp = service.getCreatives(BIZ, "WINNER", null, 50);

        assertThat(resp.total()).isEqualTo(1);
        assertThat(resp.creatives().get(0).classification()).isEqualTo("WINNER");
    }

    @Test void creativesFilterByPlatform() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(150))).thenReturn(List.of(
                scoringResult(a1, "meta", "WINNER", 5000, 200, 15, 500, 3000, 6.0, 0.04, null, "image"),
                scoringResult(a2, "google", "WINNER", 4000, 180, 12, 450, 2700, 6.0, 0.045, null, "video")));

        CreativesResponse resp = service.getCreatives(BIZ, null, "google", 50);

        assertThat(resp.total()).isEqualTo(1);
        assertThat(resp.creatives().get(0).platform()).isEqualTo("google");
    }

    @Test void creativesLimitEnforced() {
        List<ScoringResult> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(scoringResult(UUID.randomUUID(), "meta", "TESTING", 1000, 40, 2, 100, 200, 2.0, 0.04, null, "image"));
        }
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(9))).thenReturn(many);

        CreativesResponse resp = service.getCreatives(BIZ, null, null, 3);

        assertThat(resp.total()).isEqualTo(3);
    }

    @Test void creativesEmpty() {
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(150))).thenReturn(List.of());

        CreativesResponse resp = service.getCreatives(BIZ, null, null, 50);

        assertThat(resp.total()).isZero();
        assertThat(resp.creatives()).isEmpty();
    }

    // ─── Recommendations ────────────────────────────────────────────────

    @Test void recommendationsGroupedByPriority() {
        CreativeOptimizationRecommendation high = makeRec("SCALE", "HIGH");
        CreativeOptimizationRecommendation med = makeRec("TEST_MORE", "MEDIUM");
        CreativeOptimizationRecommendation low = makeRec("ADAPT_FOR_PLATFORM", "LOW");
        when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ, "OPEN"))
                .thenReturn(List.of(high, med, low));
        when(recService.availableActions(any())).thenReturn(List.of("APPLY", "DISMISS"));

        RecommendationsResponse resp = service.getRecommendations(BIZ, "OPEN");

        assertThat(resp.total()).isEqualTo(3);
        assertThat(resp.highPriority()).hasSize(1);
        assertThat(resp.mediumPriority()).hasSize(1);
        assertThat(resp.lowPriority()).hasSize(1);
    }

    @Test void recommendationsAllStatuses() {
        when(recRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

        RecommendationsResponse resp = service.getRecommendations(BIZ, null);

        assertThat(resp.total()).isZero();
    }

    @Test void recommendationsBlankStatus() {
        when(recRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

        RecommendationsResponse resp = service.getRecommendations(BIZ, "");

        assertThat(resp.total()).isZero();
    }

    // ─── Strategy ───────────────────────────────────────────────────────

    @Test void strategyWithProfileAndRecommendations() {
        BusinessProfile bp = new BusinessProfile();
        bp.setId(BIZ);
        bp.setBusinessName("Acme");
        bp.setIndustry("retail");
        bp.setTargetAudience("millennials");
        when(profileRepo.findById(BIZ)).thenReturn(Optional.of(bp));

        when(campaignRepo.aggregate(eq(BIZ), any(Instant.class))).thenReturn(
                Collections.singletonList(new Object[]{"meta", bd(1000), 50000L, 2000L, 100L, bd(5000), bd(5.0)}));

        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(500))).thenReturn(List.of(
                scoringResult(UUID.randomUUID(), "meta", "WINNER", 5000, 200, 15, 500, 3000, 6.0, 0.04, null, "image")));

        CreativeOptimizationRecommendation highRec = makeRec("SCALE", "HIGH");
        when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ, "OPEN")).thenReturn(List.of(highRec));
        when(recService.availableActions(any())).thenReturn(List.of("APPLY", "DISMISS"));

        StrategyResponse resp = service.getStrategy(BIZ);

        assertThat(resp.businessName()).isEqualTo("Acme");
        assertThat(resp.targetAudience()).isEqualTo("millennials");
        assertThat(resp.creativeHealth().winners()).isEqualTo(1);
        assertThat(resp.topRecommendations()).hasSize(1);
    }

    @Test void strategyNoProfile() {
        when(profileRepo.findById(BIZ)).thenReturn(Optional.empty());
        when(campaignRepo.aggregate(eq(BIZ), any(Instant.class))).thenReturn(List.of());
        when(scoringService.scoreAllAssets(eq(BIZ), eq(30), eq(500))).thenReturn(List.of());
        when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(BIZ, "OPEN")).thenReturn(List.of());

        StrategyResponse resp = service.getStrategy(BIZ);

        assertThat(resp.businessName()).isNull();
        assertThat(resp.campaignPerformance()).isEqualTo(SpendSummary.EMPTY);
    }

    // ─── Platforms ──────────────────────────────────────────────────────

    @Test void platformsAggregatesConnectionInsights() {
        AdPlatformConnection conn = makeConnection("META", "ACTIVE");
        when(connectionRepo.findByBusinessId(BIZ)).thenReturn(List.of(conn));

        when(insightRepo.aggregateByConnection(eq(conn.getId()), any(LocalDate.class)))
                .thenReturn(new Object[]{bd(500), 25000L, 1000L, 50L, bd(2500), 20000L});

        when(insightRepo.topCampaigns(eq(conn.getId()), any(LocalDate.class), eq(5)))
                .thenReturn(Collections.singletonList(new Object[]{"camp1", "Summer Sale", bd(300), 15000L, 600L, 30L}));

        when(insightRepo.topAds(eq(conn.getId()), any(LocalDate.class), eq(5)))
                .thenReturn(Collections.singletonList(new Object[]{"ad1", "Hero Image", bd(200), 10000L, 400L, 20L, 5.0}));

        when(mappingRepo.countByConnectionId(conn.getId())).thenReturn(12L);

        PlatformsResponse resp = service.getPlatforms(BIZ, 30);

        assertThat(resp.platforms()).hasSize(1);
        PlatformCard card = resp.platforms().get(0);
        assertThat(card.platform()).isEqualTo("META");
        assertThat(card.totalSpend()).isEqualByComparingTo(bd(500));
        assertThat(card.totalReach()).isEqualTo(20000L);
        assertThat(card.mappedAssets()).isEqualTo(12L);
        assertThat(card.topCampaigns()).hasSize(1);
        assertThat(card.topCampaigns().get(0).campaignName()).isEqualTo("Summer Sale");
        assertThat(card.topAds()).hasSize(1);
        assertThat(card.topAds().get(0).avgRoas()).isEqualTo(5.0);
    }

    @Test void platformsNoConnections() {
        when(connectionRepo.findByBusinessId(BIZ)).thenReturn(List.of());

        PlatformsResponse resp = service.getPlatforms(BIZ, 30);

        assertThat(resp.platforms()).isEmpty();
    }

    @Test void platformsNullAggregateHandledGracefully() {
        AdPlatformConnection conn = makeConnection("META", "ACTIVE");
        when(connectionRepo.findByBusinessId(BIZ)).thenReturn(List.of(conn));
        when(insightRepo.aggregateByConnection(eq(conn.getId()), any(LocalDate.class)))
                .thenReturn(new Object[]{null, null, null, null, null, null});
        when(insightRepo.topCampaigns(eq(conn.getId()), any(LocalDate.class), eq(5)))
                .thenReturn(List.of());
        when(insightRepo.topAds(eq(conn.getId()), any(LocalDate.class), eq(5)))
                .thenReturn(List.of());
        when(mappingRepo.countByConnectionId(conn.getId())).thenReturn(0L);

        PlatformsResponse resp = service.getPlatforms(BIZ, 30);

        assertThat(resp.platforms()).hasSize(1);
        PlatformCard card = resp.platforms().get(0);
        assertThat(card.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(card.topCampaigns()).isEmpty();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static BigDecimal bd(double v) { return BigDecimal.valueOf(v); }

    private ScoringResult scoringResult(UUID assetId, String platform, String classification,
                                         long imp, long clicks, long conv,
                                         double spend, double revenue, double roas, double ctr,
                                         String metadataJson, String assetType) {
        return new ScoringResult(assetId, platform, classification,
                bd(75), bd(0.85), Map.of("summary", "test"),
                imp, clicks, conv, bd(spend), bd(revenue),
                roas, ctr, spend > 0 ? spend / clicks : 0, conv > 0 ? spend / conv : 0,
                metadataJson, assetType, "prompt text");
    }

    private CreativeOptimizationRecommendation makeRec(String type, String priority) {
        CreativeOptimizationRecommendation rec = new CreativeOptimizationRecommendation();
        rec.setId(UUID.randomUUID());
        rec.setBusinessId(BIZ);
        rec.setCreativeAssetId(UUID.randomUUID());
        rec.setRecommendationType(type);
        rec.setPriority(priority);
        rec.setTitle("Test recommendation");
        rec.setDescription("Test description");
        rec.setSuggestedNextAction("Do something");
        rec.setStatus("OPEN");
        rec.setCreatedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        return rec;
    }

    private AdPlatformConnection makeConnection(String platform, String status) {
        AdPlatformConnection conn = new AdPlatformConnection();
        conn.setId(UUID.randomUUID());
        conn.setBusinessId(BIZ);
        conn.setPlatform(platform);
        conn.setConnectionName("Test Account");
        conn.setStatus(status);
        conn.setLastSyncedAt(Instant.now());
        conn.setCreatedAt(Instant.now());
        conn.setUpdatedAt(Instant.now());
        return conn;
    }
}
