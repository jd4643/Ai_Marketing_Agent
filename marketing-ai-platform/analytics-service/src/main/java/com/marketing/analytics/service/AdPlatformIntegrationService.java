package com.marketing.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.*;
import com.marketing.analytics.platform.*;
import com.marketing.analytics.platform.AdPlatformInsightNormalizer.NormalizedInsight;
import com.marketing.analytics.platform.meta.MetaAdPlatformSyncClient;
import com.marketing.analytics.platform.meta.MetaCreativeAssetMapper;
import com.marketing.analytics.platform.meta.MetaInsightNormalizer;
import com.marketing.analytics.repo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AdPlatformIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AdPlatformIntegrationService.class);

    private final AdPlatformConnectionRepository connectionRepo;
    private final AdPlatformAdRepository adRepo;
    private final AdPlatformInsightRepository insightRepo;
    private final CreativeAssetPlatformMappingRepository mappingRepo;
    private final CreativeAssetPerformanceRepository perfRepo;
    private final TokenEncryptor tokenEncryptor;
    private final MetaAdPlatformSyncClient metaSyncClient;
    private final MetaInsightNormalizer metaNormalizer;
    private final MetaCreativeAssetMapper metaMapper;
    private final DataSource ds;
    private final CreativeWinnerScoringService scoringService;
    private final CreativeOptimizationRecommendationService recommendationService;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${meta.sync.lookback-days:30}")
    int lookbackDays;

    public AdPlatformIntegrationService(
            AdPlatformConnectionRepository connectionRepo,
            AdPlatformAdRepository adRepo,
            AdPlatformInsightRepository insightRepo,
            CreativeAssetPlatformMappingRepository mappingRepo,
            CreativeAssetPerformanceRepository perfRepo,
            TokenEncryptor tokenEncryptor,
            MetaAdPlatformSyncClient metaSyncClient,
            MetaInsightNormalizer metaNormalizer,
            MetaCreativeAssetMapper metaMapper,
            DataSource ds,
            CreativeWinnerScoringService scoringService,
            CreativeOptimizationRecommendationService recommendationService) {
        this.connectionRepo = connectionRepo;
        this.adRepo = adRepo;
        this.insightRepo = insightRepo;
        this.mappingRepo = mappingRepo;
        this.perfRepo = perfRepo;
        this.tokenEncryptor = tokenEncryptor;
        this.metaSyncClient = metaSyncClient;
        this.metaNormalizer = metaNormalizer;
        this.metaMapper = metaMapper;
        this.ds = ds;
        this.scoringService = scoringService;
        this.recommendationService = recommendationService;
    }

    // ─── Connection Management ──────────────────────────────────────────

    public AdPlatformConnection connect(UUID businessId, String metaAdAccountId,
                                         String connectionName, String accessToken, String metaBusinessId) {
        verifyBusinessExists(businessId);

        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        boolean valid = metaSyncClient.validateConnection(accessToken, metaAdAccountId);
        String status = valid ? "ACTIVE" : "STUBBED";

        AdPlatformConnection conn = new AdPlatformConnection();
        conn.setId(UUID.randomUUID());
        conn.setBusinessId(businessId);
        conn.setPlatform("META");
        conn.setExternalBusinessId(metaBusinessId);
        conn.setExternalAccountId(metaAdAccountId);
        conn.setConnectionName(connectionName);
        conn.setAccessTokenEncrypted(encryptedToken);
        conn.setStatus(status);
        conn.setCreatedAt(Instant.now());
        conn.setUpdatedAt(Instant.now());

        connectionRepo.save(conn);
        log.info("Meta connection created: id={} account={} status={}", conn.getId(), metaAdAccountId, status);
        return conn;
    }

    public List<AdPlatformConnection> listConnections(UUID businessId, String platform) {
        if (platform != null && !platform.isBlank()) {
            return connectionRepo.findByBusinessIdAndPlatform(businessId, platform);
        }
        return connectionRepo.findByBusinessId(businessId);
    }

    public AdPlatformConnection disconnect(UUID connectionId) {
        AdPlatformConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        conn.setStatus("DISCONNECTED");
        conn.setUpdatedAt(Instant.now());
        connectionRepo.save(conn);
        log.info("Meta connection disconnected: id={}", connectionId);
        return conn;
    }

    // ─── Sync ───────────────────────────────────────────────────────────

    public Map<String, Object> sync(UUID connectionId) {
        AdPlatformConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        if (!"ACTIVE".equals(conn.getStatus()) && !"STUBBED".equals(conn.getStatus())) {
            throw new IllegalStateException("Connection is " + conn.getStatus() + ", cannot sync");
        }

        String accessToken = tokenEncryptor.decrypt(conn.getAccessTokenEncrypted());
        String accountId = conn.getExternalAccountId();
        UUID businessId = conn.getBusinessId();
        String requestId = MDC.get("requestId");
        log.info("Starting Meta sync for connection={} account={} requestId={}", connectionId, accountId, requestId);

        int adsSynced = syncAds(conn, accessToken, accountId);
        int insightsSynced = syncInsights(conn, accessToken, accountId);
        int assetsMapped = mapCreativeAssets(conn);
        int perfDerived = derivePerformance(conn);

        // Recompute classifications and generate recommendations after sync
        int classified = scoringService.recomputeClassifications(businessId, lookbackDays);
        recommendationService.generateAndPersist(businessId, lookbackDays);
        log.info("Post-sync classification: businessId={} classified={}", businessId, classified);

        conn.setLastSyncedAt(Instant.now());
        conn.setUpdatedAt(Instant.now());
        connectionRepo.save(conn);

        log.info("Meta sync complete: connection={} ads={} insights={} mapped={} perf={} classified={}",
                connectionId, adsSynced, insightsSynced, assetsMapped, perfDerived, classified);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("connectionId", connectionId);
        result.put("platform", "META");
        result.put("status", "SUCCESS");
        result.put("adsSynced", adsSynced);
        result.put("insightsSynced", insightsSynced);
        result.put("assetsMapped", assetsMapped);
        result.put("performanceDerived", perfDerived);
        result.put("classificationsRecomputed", classified);
        return result;
    }

    @SuppressWarnings("unchecked")
    private int syncAds(AdPlatformConnection conn, String accessToken, String accountId) {
        List<Map<String, Object>> rawAds = metaSyncClient.fetchAds(accessToken, accountId);
        int count = 0;
        for (Map<String, Object> raw : rawAds) {
            String externalAdId = str(raw, "id");
            if (externalAdId == null) continue;

            AdPlatformAd ad = adRepo.findByConnectionIdAndExternalAdId(conn.getId(), externalAdId)
                    .orElseGet(AdPlatformAd::new);
            boolean isNew = ad.getId() == null;
            if (isNew) {
                ad.setId(UUID.randomUUID());
                ad.setCreatedAt(Instant.now());
            }
            ad.setBusinessId(conn.getBusinessId());
            ad.setConnectionId(conn.getId());
            ad.setPlatform("META");
            ad.setExternalAdId(externalAdId);
            ad.setAdName(str(raw, "name"));
            ad.setStatus(str(raw, "status"));
            ad.setEffectiveStatus(str(raw, "effective_status"));

            Object campaign = raw.get("campaign");
            if (campaign instanceof Map<?, ?> cm) {
                Map<String, Object> c = (Map<String, Object>) cm;
                ad.setExternalCampaignId(str(c, "id"));
                ad.setCampaignName(str(c, "name"));
                ad.setObjective(str(c, "objective"));
            }
            ad.setExternalAdGroupId(str(raw, "adset_id"));

            Object creative = raw.get("creative");
            if (creative instanceof Map<?, ?> cr) {
                Map<String, Object> cv = (Map<String, Object>) cr;
                ad.setExternalCreativeId(str(cv, "id"));
                ad.setCreativeName(str(cv, "name"));
            }

            ad.setRawJson(toJson(raw));
            ad.setLastSeenAt(Instant.now());
            ad.setUpdatedAt(Instant.now());
            adRepo.save(ad);
            count++;
        }
        return count;
    }

    private int syncInsights(AdPlatformConnection conn, String accessToken, String accountId) {
        List<Map<String, Object>> rawInsights = metaSyncClient.fetchInsights(accessToken, accountId, lookbackDays);
        int count = 0;
        for (Map<String, Object> raw : rawInsights) {
            NormalizedInsight ni = metaNormalizer.normalize(raw);
            if (ni.externalAdId() == null) continue;

            // Avoid duplicate: check by connection + ad + date
            List<AdPlatformInsight> existing = insightRepo.findByConnectionIdAndExternalAdIdAndDateStart(
                    conn.getId(), ni.externalAdId(), ni.dateStart());
            if (!existing.isEmpty()) continue;

            AdPlatformInsight insight = new AdPlatformInsight();
            insight.setId(UUID.randomUUID());
            insight.setBusinessId(conn.getBusinessId());
            insight.setConnectionId(conn.getId());
            insight.setPlatform("META");
            insight.setExternalAdId(ni.externalAdId());
            insight.setDateStart(ni.dateStart());
            insight.setDateStop(ni.dateStop());
            insight.setImpressions(ni.impressions());
            insight.setClicks(ni.clicks());
            insight.setSpend(ni.spend());
            insight.setReach(ni.reach());
            insight.setCtr(ni.ctr());
            insight.setCpc(ni.cpc());
            insight.setCpm(ni.cpm());
            insight.setConversions(ni.conversions());
            insight.setRevenue(ni.revenue());
            insight.setRoas(ni.roas());
            insight.setActionsJson(ni.actionsJson());
            insight.setActionValuesJson(ni.actionValuesJson());
            insight.setRawJson(ni.rawJson());
            insight.setCreatedAt(Instant.now());
            insightRepo.save(insight);
            count++;
        }
        return count;
    }

    // ─── Creative Asset Mapping ─────────────────────────────────────────

    private int mapCreativeAssets(AdPlatformConnection conn) {
        List<AdPlatformAd> ads = adRepo.findByConnectionId(conn.getId());
        List<Map<String, Object>> internalAssets = loadInternalAssets(conn.getBusinessId());
        if (ads.isEmpty() || internalAssets.isEmpty()) return 0;

        // Remove existing mappings for this connection to re-map
        List<CreativeAssetPlatformMapping> existingMappings = mappingRepo.findByConnectionId(conn.getId());
        if (!existingMappings.isEmpty()) {
            mappingRepo.deleteAll(existingMappings);
        }

        List<CreativeAssetPlatformMapping> mappings = metaMapper.mapAssets(
                conn.getBusinessId(), conn.getId(), ads, internalAssets);
        mappingRepo.saveAll(mappings);
        return mappings.size();
    }

    private List<Map<String, Object>> loadInternalAssets(UUID businessId) {
        List<Map<String, Object>> assets = new ArrayList<>();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                     "SELECT id, metadata_json, asset_type, prompt_text FROM creative_assets WHERE business_id=?")) {
            ps.setObject(1, businessId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> asset = new LinkedHashMap<>();
                    asset.put("id", rs.getObject("id").toString());
                    asset.put("metadataJson", rs.getString("metadata_json"));
                    asset.put("assetType", rs.getString("asset_type"));
                    asset.put("promptText", rs.getString("prompt_text"));
                    assets.add(asset);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load internal creative assets for business={}: {}", businessId, e.getMessage());
        }
        return assets;
    }

    // ─── Derived Performance ────────────────────────────────────────────

    private int derivePerformance(AdPlatformConnection conn) {
        List<CreativeAssetPlatformMapping> mappings = mappingRepo.findByConnectionId(conn.getId());
        if (mappings.isEmpty()) return 0;

        int count = 0;
        LocalDate since = LocalDate.now().minusDays(lookbackDays);

        for (CreativeAssetPlatformMapping mapping : mappings) {
            // Aggregate insights for this external ad
            try (var c = ds.getConnection();
                 var ps = c.prepareStatement("""
                    SELECT COALESCE(SUM(impressions),0), COALESCE(SUM(clicks),0), COALESCE(SUM(conversions),0),
                           COALESCE(SUM(spend),0), COALESCE(SUM(revenue),0)
                    FROM ad_platform_insights
                    WHERE connection_id=? AND external_ad_id=? AND date_start>=?
                    """)) {
                ps.setObject(1, conn.getId());
                ps.setString(2, mapping.getExternalAdId());
                ps.setObject(3, since);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long impressions = rs.getLong(1);
                        long clicks = rs.getLong(2);
                        long conversions = rs.getLong(3);
                        BigDecimal spend = rs.getBigDecimal(4);
                        BigDecimal revenue = rs.getBigDecimal(5);
                        if (impressions == 0) continue;

                        BigDecimal ctr = impressions > 0 ? BigDecimal.valueOf((double) clicks / impressions) : null;
                        BigDecimal cpc = clicks > 0 && spend != null ? spend.divide(BigDecimal.valueOf(clicks), 4, BigDecimal.ROUND_HALF_UP) : null;
                        BigDecimal cpa = conversions > 0 && spend != null ? spend.divide(BigDecimal.valueOf(conversions), 4, BigDecimal.ROUND_HALF_UP) : null;
                        BigDecimal roas = spend != null && spend.signum() > 0 && revenue != null
                                ? revenue.divide(spend, 4, BigDecimal.ROUND_HALF_UP) : null;

                        CreativeAssetPerformance perf = new CreativeAssetPerformance();
                        perf.setId(UUID.randomUUID());
                        perf.setCreativeAssetId(mapping.getCreativeAssetId());
                        perf.setBusinessId(conn.getBusinessId());
                        perf.setPlatform("meta");
                        perf.setImpressions(impressions);
                        perf.setClicks(clicks);
                        perf.setConversions(conversions);
                        perf.setSpend(spend);
                        perf.setRevenue(revenue);
                        perf.setCtr(ctr);
                        perf.setCpc(cpc);
                        perf.setCpa(cpa);
                        perf.setRoas(roas);
                        perf.setRecordedAt(Instant.now());
                        perf.setCreatedAt(Instant.now());
                        perfRepo.save(perf);
                        count++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to derive performance for mapping={}: {}", mapping.getId(), e.getMessage());
            }
        }
        log.info("Derived {} creative_asset_performance records from Meta insights", count);
        return count;
    }

    // ─── Sync Status ────────────────────────────────────────────────────

    public Map<String, Object> syncStatus(UUID connectionId) {
        AdPlatformConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        long adCount = adRepo.findByConnectionId(connectionId).size();
        long mappedCount = mappingRepo.countByConnectionId(connectionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectionId", connectionId);
        result.put("platform", conn.getPlatform());
        result.put("status", conn.getStatus());
        result.put("lastSyncedAt", conn.getLastSyncedAt());
        result.put("externalAccountId", conn.getExternalAccountId());
        result.put("adsSynced", adCount);
        result.put("assetsMapped", mappedCount);
        return result;
    }

    // ─── Meta Insights Summary ──────────────────────────────────────────

    public Map<String, Object> insightsSummary(UUID connectionId, int days) {
        AdPlatformConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        LocalDate since = LocalDate.now().minusDays(days);

        Object[] agg = insightRepo.aggregateByConnection(connectionId, since);
        BigDecimal totalSpend = agg != null && agg[0] != null ? toBd(agg[0]) : BigDecimal.ZERO;
        long totalImpressions = agg != null && agg[1] != null ? ((Number) agg[1]).longValue() : 0;
        long totalClicks = agg != null && agg[2] != null ? ((Number) agg[2]).longValue() : 0;
        long totalConversions = agg != null && agg[3] != null ? ((Number) agg[3]).longValue() : 0;
        BigDecimal totalRevenue = agg != null && agg[4] != null ? toBd(agg[4]) : BigDecimal.ZERO;
        long totalReach = agg != null && agg[5] != null ? ((Number) agg[5]).longValue() : 0;

        List<Object[]> topCampaigns = insightRepo.topCampaigns(connectionId, since, 5);
        List<Map<String, Object>> campaigns = new ArrayList<>();
        for (Object[] row : topCampaigns) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("externalCampaignId", row[0]);
            c.put("campaignName", row[1]);
            c.put("spend", row[2]);
            c.put("impressions", row[3]);
            c.put("clicks", row[4]);
            c.put("conversions", row[5]);
            campaigns.add(c);
        }

        List<Object[]> topAdsRows = insightRepo.topAds(connectionId, since, 5);
        List<Map<String, Object>> topAds = new ArrayList<>();
        for (Object[] row : topAdsRows) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("externalAdId", row[0]);
            a.put("adName", row[1]);
            a.put("spend", row[2]);
            a.put("impressions", row[3]);
            a.put("clicks", row[4]);
            a.put("conversions", row[5]);
            a.put("avgRoas", row[6]);
            topAds.add(a);
        }

        long mappedCount = mappingRepo.countByConnectionId(connectionId);
        long totalAds = adRepo.findByConnectionId(connectionId).size();
        long unmappedCount = totalAds - mappedCount;

        // Winning hooks from mapped assets
        List<String> winningHooks = extractWinningHooks(connectionId, since);

        List<String> recommendations = new ArrayList<>();
        if (totalSpend.signum() > 0 && totalRevenue.signum() > 0) {
            BigDecimal overallRoas = totalRevenue.divide(totalSpend, 2, BigDecimal.ROUND_HALF_UP);
            recommendations.add("Overall ROAS: " + overallRoas + "x across " + days + " days.");
        }
        if (mappedCount > 0) recommendations.add(mappedCount + " Meta ads mapped to internal creative assets — performance feeds into winner detection.");
        if (unmappedCount > 0) recommendations.add(unmappedCount + " Meta ads not yet mapped. Consider naming ads to match creative concepts for better tracking.");
        if (!winningHooks.isEmpty()) recommendations.add("Winning hooks from Meta: " + String.join(", ", winningHooks));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectionId", connectionId);
        result.put("platform", "META");
        result.put("days", days);
        result.put("totalSpend", totalSpend);
        result.put("totalImpressions", totalImpressions);
        result.put("totalClicks", totalClicks);
        result.put("totalConversions", totalConversions);
        result.put("totalRevenue", totalRevenue);
        result.put("totalReach", totalReach);
        result.put("topCampaigns", campaigns);
        result.put("topAds", topAds);
        result.put("mappedAssets", mappedCount);
        result.put("unmappedAds", unmappedCount);
        result.put("winningHooks", winningHooks);
        result.put("recommendations", recommendations);
        return result;
    }

    private List<String> extractWinningHooks(UUID connectionId, LocalDate since) {
        List<String> hooks = new ArrayList<>();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                SELECT ca.metadata_json, AVG(api.roas) as avg_roas
                FROM creative_asset_platform_mapping capm
                JOIN creative_assets ca ON ca.id = capm.creative_asset_id
                JOIN ad_platform_insights api ON api.connection_id = capm.connection_id
                    AND api.external_ad_id = capm.external_ad_id AND api.date_start >= ?
                WHERE capm.connection_id = ?
                GROUP BY ca.metadata_json
                HAVING AVG(api.roas) >= 2.0
                ORDER BY avg_roas DESC LIMIT 5
                """)) {
            ps.setObject(1, since);
            ps.setObject(2, connectionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String metaJson = rs.getString(1);
                    if (metaJson != null) {
                        try {
                            Map<String, Object> meta = om.readValue(metaJson, new TypeReference<>() {});
                            String hook = meta.get("hook") != null ? meta.get("hook").toString() : null;
                            if (hook != null) hooks.add(hook);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract winning hooks: {}", e.getMessage());
        }
        return hooks;
    }

    // ─── Scheduled Sync ─────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${meta.sync.interval-ms:3600000}", initialDelayString = "${meta.sync.initial-delay-ms:60000}")
    public void scheduledSync() {
        List<AdPlatformConnection> active = connectionRepo.findByStatus("ACTIVE");
        if (active.isEmpty()) return;
        log.info("Scheduled Meta sync: processing {} active connections", active.size());
        for (AdPlatformConnection conn : active) {
            try {
                MDC.put("requestId", "scheduled-" + UUID.randomUUID().toString().substring(0, 8));
                sync(conn.getId());
            } catch (Exception e) {
                log.error("Scheduled sync failed for connection={}: {}", conn.getId(), e.getMessage());
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    // ─── Utilities ──────────────────────────────────────────────────────

    private void verifyBusinessExists(UUID businessId) {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM business_profile WHERE id=?")) {
            ps.setObject(1, businessId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Business not found: " + businessId);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify business: " + e.getMessage(), e);
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return om.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    private BigDecimal toBd(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
