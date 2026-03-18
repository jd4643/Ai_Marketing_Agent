package com.marketing.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;

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
 * Generates actionable optimization recommendations based on creative asset classifications.
 *
 * <p>Recommendation types:
 * <ul>
 *   <li>SCALE: asset is WINNER with good confidence and healthy volume</li>
 *   <li>STOP: asset is WEAK with sufficient data and poor performance</li>
 *   <li>TEST_MORE: asset is TESTING with promising early signals</li>
 *   <li>DUPLICATE_WINNER: winner should be turned into more variations</li>
 *   <li>ADAPT_FOR_PLATFORM: winner on one platform might work on another</li>
 * </ul>
 */
@Service
public class CreativeOptimizationRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(CreativeOptimizationRecommendationService.class);
    private final CreativeWinnerScoringService scoringService;
    private final CreativeOptimizationRecommendationRepository recRepo;
    private final ObjectMapper om = new ObjectMapper();

    public CreativeOptimizationRecommendationService(CreativeWinnerScoringService scoringService,
                                                      CreativeOptimizationRecommendationRepository recRepo) {
        this.scoringService = scoringService;
        this.recRepo = recRepo;
    }

    public record RecommendationResult(
            String recommendationType,
            String priority,
            String title,
            String description,
            UUID relatedCreativeAssetId,
            Map<String, Object> metricsSummary,
            Map<String, Object> reasoning,
            String suggestedNextAction
    ) {}

    /**
     * Generate recommendations for a business based on current creative performance.
     */
    public List<RecommendationResult> generateRecommendations(UUID businessId, int days) {
        List<ScoringResult> scored = scoringService.scoreAllAssets(businessId, days, 200);
        List<RecommendationResult> recs = new ArrayList<>();

        Set<String> seenPlatforms = new HashSet<>();
        List<ScoringResult> winners = new ArrayList<>();
        List<ScoringResult> weak = new ArrayList<>();
        List<ScoringResult> testing = new ArrayList<>();

        for (ScoringResult sr : scored) {
            seenPlatforms.add(sr.platform());
            switch (sr.classification()) {
                case "WINNER" -> winners.add(sr);
                case "WEAK" -> weak.add(sr);
                case "TESTING" -> testing.add(sr);
            }
        }

        // SCALE recommendations for winners
        for (ScoringResult w : winners) {
            if (w.confidenceScore().doubleValue() >= 0.50) {
                String hook = extractField(w.metadataJson(), "hook");
                String hookInfo = hook != null ? " (hook: '" + hook + "')" : "";
                recs.add(new RecommendationResult(
                        "SCALE",
                        w.confidenceScore().doubleValue() >= 0.70 ? "HIGH" : "MEDIUM",
                        "Scale winning " + (w.assetType() != null ? w.assetType() : "creative") + " on " + w.platform() + hookInfo,
                        "This asset shows strong ROAS (" + bd(w.avgRoas()) + "x) and " + w.conversions()
                                + " conversions on " + w.platform() + ". Increase budget allocation or produce closely related variants.",
                        w.creativeAssetId(),
                        buildMetrics(w),
                        w.reasoning(),
                        "Increase daily budget by 20% or generate 3 more variants using POST /generate/creative-assets/from-winner"
                ));
            }
        }

        // STOP recommendations for weak performers
        for (ScoringResult wk : weak) {
            if (wk.confidenceScore().doubleValue() >= 0.40) {
                recs.add(new RecommendationResult(
                        "STOP",
                        wk.avgRoas() <= 0.3 ? "HIGH" : "MEDIUM",
                        "Pause weak " + (wk.assetType() != null ? wk.assetType() : "creative") + " on " + wk.platform(),
                        "This asset has ROAS of " + bd(wk.avgRoas()) + "x with " + wk.impressions()
                                + " impressions. It is consuming budget without adequate return.",
                        wk.creativeAssetId(),
                        buildMetrics(wk),
                        wk.reasoning(),
                        "Pause this ad set and reallocate budget to winning creatives"
                ));
            }
        }

        // TEST_MORE recommendations for promising testing assets
        for (ScoringResult t : testing) {
            if (t.avgCtr() >= 0.01 && t.impressions() >= 1000) {
                recs.add(new RecommendationResult(
                        "TEST_MORE",
                        "MEDIUM",
                        "Continue testing " + (t.assetType() != null ? t.assetType() : "creative") + " — promising CTR",
                        "This asset shows " + bd(t.avgCtr() * 100) + "% CTR with " + t.impressions()
                                + " impressions but needs more conversions data. Let it run to " + scoringService.minImpressions + " impressions.",
                        t.creativeAssetId(),
                        buildMetrics(t),
                        t.reasoning(),
                        "Maintain current budget and wait for " + scoringService.minImpressions + " impressions before judging"
                ));
            }
        }

        // DUPLICATE_WINNER — suggest creating variations of winners
        for (ScoringResult w : winners) {
            if (w.confidenceScore().doubleValue() >= 0.60 && w.conversions() >= 5) {
                String hook = extractField(w.metadataJson(), "hook");
                String style = extractField(w.metadataJson(), "visualStyle");
                String description = "Winner asset with ROAS " + bd(w.avgRoas()) + "x should be expanded into more variations.";
                if (hook != null) description += " Winning hook: '" + hook + "'.";
                if (style != null) description += " Visual style: " + style + ".";
                recs.add(new RecommendationResult(
                        "DUPLICATE_WINNER",
                        "HIGH",
                        "Create variations of winning " + (w.assetType() != null ? w.assetType() : "asset"),
                        description,
                        w.creativeAssetId(),
                        buildMetrics(w),
                        w.reasoning(),
                        "POST /generate/creative-assets/from-winner with variationType=iteration and count=3"
                ));
            }
        }

        // ADAPT_FOR_PLATFORM — suggest porting winners to other platforms
        if (seenPlatforms.size() > 0) {
            Set<String> allPlatforms = Set.of("meta", "google", "tiktok");
            for (ScoringResult w : winners) {
                if (w.confidenceScore().doubleValue() >= 0.60) {
                    for (String targetPlatform : allPlatforms) {
                        if (!targetPlatform.equalsIgnoreCase(w.platform())) {
                            recs.add(new RecommendationResult(
                                    "ADAPT_FOR_PLATFORM",
                                    "LOW",
                                    "Adapt winning " + w.platform() + " asset for " + targetPlatform,
                                    "This asset performs well on " + w.platform() + " (ROAS " + bd(w.avgRoas())
                                            + "x). Consider adapting it for " + targetPlatform + " to test cross-platform performance.",
                                    w.creativeAssetId(),
                                    buildMetrics(w),
                                    w.reasoning(),
                                    "POST /generate/creative-assets/from-winner with platform=" + targetPlatform
                            ));
                            break; // one adaptation suggestion per winner
                        }
                    }
                }
            }
        }

        return recs;
    }

    /**
     * Generate and persist recommendations, replacing previous OPEN recommendations.
     */
    @Transactional
    public List<RecommendationResult> generateAndPersist(UUID businessId, int days) {
        String requestId = MDC.get("requestId");
        log.info("Generating recommendations for businessId={} days={} requestId={}", businessId, days, requestId);

        List<RecommendationResult> recs = generateRecommendations(businessId, days);

        // Clear old OPEN recommendations for this business
        recRepo.deleteByBusinessIdAndStatus(businessId, "OPEN");

        // Persist new recommendations
        for (RecommendationResult r : recs) {
            CreativeOptimizationRecommendation entity = new CreativeOptimizationRecommendation();
            entity.setId(UUID.randomUUID());
            entity.setBusinessId(businessId);
            entity.setCreativeAssetId(r.relatedCreativeAssetId());
            entity.setRecommendationType(r.recommendationType());
            entity.setPriority(r.priority());
            entity.setTitle(r.title());
            entity.setDescription(r.description());
            try {
                entity.setReasoningJson(om.writeValueAsString(r.reasoning()));
            } catch (Exception e) {
                entity.setReasoningJson(null);
            }
            entity.setStatus("OPEN");
            entity.setCreatedAt(Instant.now());
            recRepo.save(entity);
        }

        log.info("Generated {} recommendations for businessId={}", recs.size(), businessId);
        return recs;
    }

    /**
     * Fetch persisted recommendations for a business.
     */
    public List<Map<String, Object>> getPersistedRecommendations(UUID businessId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<CreativeOptimizationRecommendation> entities =
                recRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(businessId, since);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CreativeOptimizationRecommendation e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("recommendationType", e.getRecommendationType());
            item.put("priority", e.getPriority());
            item.put("title", e.getTitle());
            item.put("description", e.getDescription());
            item.put("relatedCreativeAssetId", e.getCreativeAssetId());
            item.put("status", e.getStatus());
            item.put("createdAt", e.getCreatedAt());
            if (e.getReasoningJson() != null) {
                try {
                    item.put("reasoning", om.readValue(e.getReasoningJson(), new TypeReference<Map<String, Object>>() {}));
                } catch (Exception ex) {
                    item.put("reasoning", e.getReasoningJson());
                }
            }
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildMetrics(ScoringResult sr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("impressions", sr.impressions());
        m.put("clicks", sr.clicks());
        m.put("conversions", sr.conversions());
        m.put("spend", sr.spend());
        m.put("revenue", sr.revenue());
        m.put("roas", bd(sr.avgRoas()));
        m.put("ctr", bd(sr.avgCtr()));
        m.put("performanceScore", sr.performanceScore());
        m.put("confidenceScore", sr.confidenceScore());
        return m;
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

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
