package com.marketing.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles recommendation lifecycle transitions: apply, dismiss, enrichment.
 * Bridges recommendation data to generation and export workflows.
 */
@Service
public class RecommendationActionService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationActionService.class);
    private final CreativeOptimizationRecommendationRepository recRepo;
    private final CreativeOptimizationRecommendationService recService;
    private final OutcomeTrackingService outcomeTrackingService;
    private final ObjectMapper om = new ObjectMapper();

    public RecommendationActionService(CreativeOptimizationRecommendationRepository recRepo,
                                       CreativeOptimizationRecommendationService recService,
                                       OutcomeTrackingService outcomeTrackingService) {
        this.recRepo = recRepo;
        this.recService = recService;
        this.outcomeTrackingService = outcomeTrackingService;
    }

    /**
     * Mark a recommendation as APPLIED. Idempotent — if already applied, returns current state.
     */
    @Transactional
    public Map<String, Object> apply(UUID recommendationId) {
        String requestId = MDC.get("requestId");
        CreativeOptimizationRecommendation rec = recRepo.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if ("APPLIED".equals(rec.getStatus())) {
            log.info("Recommendation {} already APPLIED, returning idempotent response requestId={}", recommendationId, requestId);
            return recService.entityToMap(rec);
        }
        if ("DISMISSED".equals(rec.getStatus())) {
            throw new IllegalArgumentException("Cannot apply a dismissed recommendation: " + recommendationId);
        }

        rec.setStatus("APPLIED");
        rec.setAppliedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        recRepo.save(rec);
        log.info("Recommendation {} marked APPLIED requestId={}", recommendationId, requestId);

        // Record baseline for closed-loop outcome tracking
        try {
            outcomeTrackingService.recordBaseline(recommendationId, "APPLIED");
        } catch (Exception e) {
            log.warn("Failed to record outcome baseline for recommendation={}: {}", recommendationId, e.getMessage());
        }

        return recService.entityToMap(rec);
    }

    /**
     * Mark a recommendation as DISMISSED. Idempotent — if already dismissed, returns current state.
     */
    @Transactional
    public Map<String, Object> dismiss(UUID recommendationId) {
        String requestId = MDC.get("requestId");
        CreativeOptimizationRecommendation rec = recRepo.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if ("DISMISSED".equals(rec.getStatus())) {
            log.info("Recommendation {} already DISMISSED, returning idempotent response requestId={}", recommendationId, requestId);
            return recService.entityToMap(rec);
        }
        if ("APPLIED".equals(rec.getStatus())) {
            throw new IllegalArgumentException("Cannot dismiss an applied recommendation: " + recommendationId);
        }

        rec.setStatus("DISMISSED");
        rec.setDismissedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        recRepo.save(rec);
        log.info("Recommendation {} marked DISMISSED requestId={}", recommendationId, requestId);

        // Record baseline for closed-loop outcome tracking
        try {
            outcomeTrackingService.recordBaseline(recommendationId, "DISMISSED");
        } catch (Exception e) {
            log.warn("Failed to record outcome baseline for recommendation={}: {}", recommendationId, e.getMessage());
        }

        return recService.entityToMap(rec);
    }

    /**
     * Get full detail for a single recommendation.
     */
    public Map<String, Object> getDetail(UUID recommendationId) {
        CreativeOptimizationRecommendation rec = recRepo.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));
        return recService.entityToMap(rec);
    }

    /**
     * List recommendations for a business, optionally filtered by status and time window.
     */
    public List<Map<String, Object>> list(UUID businessId, String status, int days) {
        return recService.getPersistedRecommendations(businessId, status, days);
    }

    /**
     * Get dashboard-friendly recommendations grouped by priority.
     */
    public Map<String, Object> dashboard(UUID businessId) {
        List<CreativeOptimizationRecommendation> all =
                recRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);

        List<Map<String, Object>> high = new ArrayList<>();
        List<Map<String, Object>> medium = new ArrayList<>();
        List<Map<String, Object>> low = new ArrayList<>();

        for (CreativeOptimizationRecommendation rec : all) {
            Map<String, Object> card = buildDashboardCard(rec);
            switch (rec.getPriority()) {
                case "HIGH" -> high.add(card);
                case "MEDIUM" -> medium.add(card);
                default -> low.add(card);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessId", businessId);
        result.put("totalRecommendations", all.size());
        result.put("highPriority", high);
        result.put("mediumPriority", medium);
        result.put("lowPriority", low);
        return result;
    }

    /**
     * Build an export-ready launch package from a recommendation.
     * This combines recommendation context, asset data, and guidance into a single actionable package.
     */
    public Map<String, Object> exportLaunchPackage(UUID businessId, UUID recommendationId) {
        CreativeOptimizationRecommendation rec = recRepo.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if (!rec.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Recommendation does not belong to business: " + businessId);
        }

        Map<String, Object> reasoning = parseJsonSafe(rec.getReasoningJson());
        Map<String, Object> metadata = parseJsonSafe(rec.getMetadataJson());

        String platform = derivePlatform(rec, reasoning, metadata);
        String objective = deriveObjective(rec);

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("requestId", MDC.get("requestId"));
        pkg.put("businessId", businessId);
        pkg.put("recommendationId", recommendationId);
        pkg.put("campaignName", deriveCampaignName(rec));
        pkg.put("platform", platform);
        pkg.put("objective", objective);
        pkg.put("budgetGuidance", deriveBudgetGuidance(rec, reasoning));
        pkg.put("targetingGuidance", deriveTargetingGuidance(rec, reasoning));

        pkg.put("copy", buildCopyBlock(rec, metadata));

        List<Map<String, Object>> assetLinks = new ArrayList<>();
        if (rec.getCreativeAssetId() != null) {
            assetLinks.add(Map.of("assetId", rec.getCreativeAssetId(), "url", "Fetch via GET /generate/assets/" + rec.getCreativeAssetId()));
        }
        pkg.put("assetLinks", assetLinks);

        pkg.put("landingPageGuidance", deriveLandingPageGuidance(rec));
        pkg.put("trackingChecklist", buildTrackingChecklist(platform));
        pkg.put("notes", buildLaunchNotes(rec));

        pkg.put("generationServiceLinks", buildGenerationServiceLinks(businessId, recommendationId, platform, objective));

        return pkg;
    }

    private Map<String, Object> buildGenerationServiceLinks(UUID businessId, UUID recommendationId,
                                                              String platform, String objective) {
        Map<String, Object> links = new LinkedHashMap<>();

        Map<String, Object> landingPage = new LinkedHashMap<>();
        landingPage.put("endpoint", "POST /generate/landing-page");
        landingPage.put("description", "Generate an AI-powered landing page for this campaign");
        landingPage.put("suggestedPayload", Map.of(
                "business_id", businessId.toString(),
                "platform", platform,
                "objective", objective
        ));
        links.put("generateLandingPage", landingPage);

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("endpoint", "POST /generate/offer");
        offer.put("description", "Generate a promotional offer with copy variants");
        offer.put("suggestedPayload", Map.of(
                "business_id", businessId.toString(),
                "platform", platform,
                "offer_type", "percentage_discount"
        ));
        links.put("generateOffer", offer);

        Map<String, Object> launchPkg = new LinkedHashMap<>();
        launchPkg.put("endpoint", "POST /generate/launch-package");
        launchPkg.put("description", "Generate a comprehensive AI-enhanced launch package combining landing page, offer, and campaign strategy");
        launchPkg.put("suggestedPayload", Map.of(
                "business_id", businessId.toString(),
                "recommendation_id", recommendationId.toString(),
                "platform", platform
        ));
        links.put("generateEnhancedLaunchPackage", launchPkg);

        return links;
    }

    private Map<String, Object> buildDashboardCard(CreativeOptimizationRecommendation rec) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("recommendationId", rec.getId());
        card.put("title", rec.getTitle());
        card.put("type", rec.getRecommendationType());
        card.put("priority", rec.getPriority());
        card.put("status", rec.getStatus());
        card.put("relatedAssetId", rec.getCreativeAssetId());
        card.put("whyThisMatters", rec.getDescription());
        card.put("availableActions", recService.availableActions(rec));
        return card;
    }

    private String derivePlatform(CreativeOptimizationRecommendation rec, Map<String, Object> reasoning, Map<String, Object> metadata) {
        if (reasoning != null && reasoning.get("platform") != null) return reasoning.get("platform").toString();
        if (metadata != null && metadata.get("platform") != null) return metadata.get("platform").toString();
        if (rec.getTitle() != null) {
            String t = rec.getTitle().toLowerCase();
            if (t.contains("meta") || t.contains("facebook") || t.contains("instagram")) return "meta";
            if (t.contains("google")) return "google";
            if (t.contains("tiktok")) return "tiktok";
            if (t.contains("youtube")) return "youtube";
        }
        return "meta";
    }

    private String deriveObjective(CreativeOptimizationRecommendation rec) {
        return switch (rec.getRecommendationType()) {
            case "SCALE" -> "conversions";
            case "STOP" -> "pause_and_reallocate";
            case "TEST_MORE" -> "traffic";
            case "DUPLICATE_WINNER" -> "conversions";
            case "ADAPT_FOR_PLATFORM" -> "awareness";
            default -> "conversions";
        };
    }

    private String deriveCampaignName(CreativeOptimizationRecommendation rec) {
        String type = rec.getRecommendationType().toLowerCase().replace("_", "-");
        String assetSuffix = rec.getCreativeAssetId() != null
                ? "-" + rec.getCreativeAssetId().toString().substring(0, 8) : "";
        return type + assetSuffix + "-" + java.time.LocalDate.now();
    }

    private String deriveBudgetGuidance(CreativeOptimizationRecommendation rec, Map<String, Object> reasoning) {
        return switch (rec.getRecommendationType()) {
            case "SCALE" -> "Increase daily budget by 20-30% on this asset. If ROAS remains stable after 3 days, continue scaling incrementally.";
            case "STOP" -> "Reallocate this asset's budget to winning creatives immediately.";
            case "TEST_MORE" -> "Maintain current budget. Do not increase until classification reaches WINNER.";
            case "DUPLICATE_WINNER" -> "Allocate 50% of the original winner's budget across new variants for A/B testing.";
            case "ADAPT_FOR_PLATFORM" -> "Start with a small test budget ($10-20/day) on the new platform.";
            default -> "Follow standard budget allocation guidelines.";
        };
    }

    private String deriveTargetingGuidance(CreativeOptimizationRecommendation rec, Map<String, Object> reasoning) {
        return switch (rec.getRecommendationType()) {
            case "SCALE" -> "Maintain current targeting. Consider expanding lookalike audiences from converters.";
            case "STOP" -> "Not applicable — pause this asset.";
            case "TEST_MORE" -> "Keep current targeting. Ensure audience size is sufficient for statistical significance.";
            case "DUPLICATE_WINNER" -> "Use same targeting as original winner. Test one variable at a time.";
            case "ADAPT_FOR_PLATFORM" -> "Mirror the original platform's targeting as closely as possible on the new platform.";
            default -> "Use best-performing audience segments.";
        };
    }

    private Map<String, Object> buildCopyBlock(CreativeOptimizationRecommendation rec, Map<String, Object> metadata) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (metadata != null) {
            copy.put("headline", metadata.getOrDefault("headline", rec.getTitle()));
            copy.put("primaryText", metadata.getOrDefault("primaryText", rec.getDescription()));
            copy.put("cta", metadata.getOrDefault("cta", deriveDefaultCta(rec)));
        } else {
            copy.put("headline", rec.getTitle());
            copy.put("primaryText", rec.getDescription());
            copy.put("cta", deriveDefaultCta(rec));
        }
        return copy;
    }

    private String deriveDefaultCta(CreativeOptimizationRecommendation rec) {
        return switch (rec.getRecommendationType()) {
            case "SCALE", "DUPLICATE_WINNER" -> "Shop Now";
            case "TEST_MORE" -> "Learn More";
            case "ADAPT_FOR_PLATFORM" -> "Discover";
            default -> "Learn More";
        };
    }

    private String deriveLandingPageGuidance(CreativeOptimizationRecommendation rec) {
        return switch (rec.getRecommendationType()) {
            case "SCALE" -> "Use the same landing page as the winning ad. Ensure page load < 3s and clear conversion path.";
            case "STOP" -> "Not applicable — this asset should be paused.";
            case "TEST_MORE" -> "Use a well-optimized landing page. Consider A/B testing the landing page alongside the creative.";
            case "DUPLICATE_WINNER" -> "Use the winning landing page. Ensure consistency between ad creative and landing page messaging.";
            case "ADAPT_FOR_PLATFORM" -> "Adapt landing page for the new platform's audience expectations and device patterns.";
            default -> "Ensure landing page matches ad messaging and loads quickly.";
        };
    }

    private List<String> buildTrackingChecklist(String platform) {
        List<String> checklist = new ArrayList<>();
        checklist.add("Verify " + platform + " pixel/conversion API is installed and firing");
        checklist.add("Confirm conversion events are properly mapped (Purchase, Lead, AddToCart)");
        checklist.add("Set up UTM parameters for campaign tracking");
        checklist.add("Enable server-side conversion tracking if available");
        checklist.add("Create custom conversion window matching your sales cycle");
        checklist.add("Verify attribution model matches business goals");
        return checklist;
    }

    private List<String> buildLaunchNotes(CreativeOptimizationRecommendation rec) {
        List<String> notes = new ArrayList<>();
        notes.add("Recommendation type: " + rec.getRecommendationType());
        notes.add("Priority: " + rec.getPriority());
        if (rec.getSuggestedNextAction() != null) {
            notes.add("Suggested action: " + rec.getSuggestedNextAction());
        }
        if (rec.getCreativeAssetId() != null) {
            notes.add("Related asset: " + rec.getCreativeAssetId());
        }
        notes.add("Review campaign performance 72 hours after launch before making changes.");
        return notes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
