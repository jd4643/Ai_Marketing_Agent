package com.marketing.strategy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.strategy.model.*;
import com.marketing.strategy.service.intel.*;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StrategyService {
    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);
    private final DataSource ds;
    private final ObjectMapper om = new ObjectMapper();
    private final OkHttpClient client;
    private final StrategyTemplateRepository templateRepository;
    private final StrategyRunIntelRepository intelRepository;
    private final DecisionTreeSelector decisionTreeSelector;
    private final ConfidenceScorer confidenceScorer;
    private final PatternMatcher patternMatcher;
    private final IntelPromptBuilder promptBuilder;
    @Value("${openai.api-key:}")
    String apiKey;
    @Value("${openai.model:gpt-4o-mini}")
    String model;

    public StrategyService(DataSource ds, @Value("${openai.timeout-seconds:15}") int timeout,
                           StrategyTemplateRepository templateRepository, StrategyRunIntelRepository intelRepository,
                           DecisionTreeSelector decisionTreeSelector, ConfidenceScorer confidenceScorer, PatternMatcher patternMatcher, IntelPromptBuilder promptBuilder) {
        this.ds = ds;
        this.templateRepository = templateRepository;
        this.intelRepository = intelRepository;
        this.decisionTreeSelector = decisionTreeSelector;
        this.confidenceScorer = confidenceScorer;
        this.patternMatcher = patternMatcher;
        this.promptBuilder = promptBuilder;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)    // per-attempt
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public StrategyResponse generate(StrategyRequest req, UUID requestId) {
        Map<String, Object> business = getBusiness(req.businessId());
        if (business.isEmpty()) throw new IllegalArgumentException("businessId not found");
        List<Map<String, Object>> metrics = metrics(req.businessId());
        List<Map<String, Object>> winners = winners(req.businessId());
        List<Map<String, Object>> assetWinnersList = assetWinners(req.businessId());

        // Cold start = no performance history + no creative winners yet
        boolean coldStart = (metrics == null || metrics.isEmpty())
                && (winners == null || winners.isEmpty());

        // Treat "no conversions" as cold start even if metric rows exist
        if (!coldStart && metrics != null && !metrics.isEmpty()) {
            long totalConversions = metricConversions(metrics);
            coldStart = totalConversions == 0 && (winners == null || winners.isEmpty());
        }

        if (coldStart) {
            log.info("Cold start detected for businessId={} — no performance history or creative winners", req.businessId());
        }

        List<String> trends = req.trends() != null && !req.trends().isEmpty() ? req.trends() : trendsByIndustry((String) business.get("industry"));
        String perfSummary = buildPerfSummary(metrics, winners, assetWinnersList);
        Map<String, BigDecimal> split = deterministicSplit(req.monthlyBudget(), metrics);

        DecisionTreeSelector.SelectionResult selection = decisionTreeSelector.select(business, req.objective(), req.monthlyBudget(), coldStart);
        String chosenTemplateKey = selection.templateKey();

        StrategyRunIntelEntity latest = latestIntel(req.businessId());
        Map<String, Object> latestPerf = latest == null ? Map.of() : performanceForRequest(req.businessId(), latest.getRequestId());

        // Skip pattern matching on cold start — no historical data to match against
        Optional<PatternMatcher.MatchResult> match;
        if (coldStart) {
            match = Optional.empty();
            log.info("Cold start: skipping pattern matching for businessId={}", req.businessId());
        } else {
            match = patternMatcher.match(chosenTemplateKey, req.objective(), (String) business.get("industry"), selection.motion(), selection.budgetTier(),
                    BigDecimal.valueOf(metricRoas(metrics)), metricConversions(metrics), latest, latestPerf);
        }
        if (match.isPresent()) chosenTemplateKey = match.get().templateKey();

        ConfidenceScorer.ConfidenceResult confidence = confidenceScorer.score(selection.motion(), req.objective() == null ? "sales" : req.objective().toLowerCase(), trends.size(), metricConversions(metrics), coldStart);

        saveIntel(requestId, req, chosenTemplateKey, selection.decisionPath(), confidence, match.map(PatternMatcher.MatchResult::similarityMatch).orElse(null));

        String finalChosenTemplateKey = chosenTemplateKey;
        StrategyTemplateEntity template = templateRepository.findByTemplateKey(chosenTemplateKey)
                .orElseThrow(() -> new IllegalStateException("strategy template missing: " + finalChosenTemplateKey));
        String prompt = promptBuilder.build(business, req.objective(), req.monthlyBudget(), template, perfSummary, trends, confidence.confidenceScore(), coldStart);

        Map<String, Object> llmResp;
        long start = System.currentTimeMillis();
        try {
            log.info("Calling OpenAI model={} apiKeyPresent={} promptLength={}", model, apiKey != null && !apiKey.isBlank(), prompt.length());
            llmResp = callOpenAi(prompt);
            log.info("OpenAI call succeeded in {}ms, keys={}", System.currentTimeMillis() - start, llmResp.keySet());

            // Anti-generic validation: reject template-like LLM output and retry once
            List<String> genericViolations = validateNotGeneric(llmResp, business);
            if (!genericViolations.isEmpty()) {
                log.warn("Anti-generic validation failed (attempt 1): {}", genericViolations);
                String retryPrompt = prompt + "\n\n=== RETRY INSTRUCTION ===\n"
                        + "Your previous output was rejected because it was too generic.\n"
                        + "Violations: " + genericViolations + "\n"
                        + "You MUST fix these issues. Reference the business name, industry, and product directly. "
                        + "Include specific execution steps, creative strategy, and setup checklist. "
                        + "Do NOT return a flat budget template.\n";
                try {
                    llmResp = callOpenAi(retryPrompt);
                    List<String> retryViolations = validateNotGeneric(llmResp, business);
                    if (!retryViolations.isEmpty()) {
                        log.warn("Anti-generic validation failed on retry too: {}. Enriching with deterministic fallback sections.", retryViolations);
                        llmResp = enrichWithFallbackSections(llmResp, business, template, split, coldStart);
                    }
                } catch (Exception retryEx) {
                    log.error("OpenAI retry call failed: {}", retryEx.getMessage());
                    llmResp = enrichWithFallbackSections(llmResp, business, template, split, coldStart);
                }
            }
        } catch (Exception ex) {
            log.error("OpenAI call failed after {}ms: {}", System.currentTimeMillis() - start, ex.getMessage(), ex);
            llmResp = fallback(requestId, split, template, confidence.confidenceScore(), selection.decisionPath(), business, coldStart);
            saveHistory(requestId, req, prompt, llmResp, "FAILED", "OPENAI_ERROR", ex.getMessage(), System.currentTimeMillis() - start);
            return toResponse(llmResp);
        }
        llmResp = normalizeOrFallback(llmResp, requestId, split, business, template, coldStart);
        // Inject creative asset winner insights
        if (!assetWinnersList.isEmpty()) {
            llmResp.put("winnerInsights", buildWinnerInsights(assetWinnersList));
            llmResp.put("recommendedNextCreativeMoves", buildCreativeMoves(assetWinnersList));
        }
        saveHistory(requestId, req, prompt, llmResp, "SUCCESS", null, null, System.currentTimeMillis() - start);
        return toResponse(llmResp);
    }

    public String buildPerfSummary(List<Map<String, Object>> metrics, List<Map<String, Object>> winners, List<Map<String, Object>> assetWinners) {
        StringBuilder sb = new StringBuilder();
        sb.append("metrics=");
        if (metrics == null || metrics.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            for (int i = 0; i < metrics.size(); i++) {
                Map<String, Object> m = metrics.get(i);
                if (i > 0) sb.append(", ");
                sb.append("{platform=").append(m.get("platform"))
                        .append(", spend=").append(m.get("spend"))
                        .append(", avgRoas=").append(m.get("avgRoas"))
                        .append(", conversions=").append(m.get("conversions")).append("}");
            }
            sb.append("]");
        }
        sb.append(" winners=");
        if (winners == null || winners.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            for (int i = 0; i < winners.size(); i++) {
                Map<String, Object> w = winners.get(i);
                if (i > 0) sb.append(", ");
                sb.append("{platform=").append(w.get("platform"))
                        .append(", hook=").append(w.get("hook"))
                        .append(", angle=").append(w.get("angle"))
                        .append(", score=").append(w.get("score")).append("}");
            }
            sb.append("]");
        }
        sb.append(" assetWinners=");
        if (assetWinners == null || assetWinners.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            for (int i = 0; i < assetWinners.size(); i++) {
                Map<String, Object> aw = assetWinners.get(i);
                if (i > 0) sb.append(", ");
                sb.append("{assetId=").append(aw.get("assetId"))
                        .append(", platform=").append(aw.get("platform"))
                        .append(", roas=").append(aw.get("avgRoas"))
                        .append(", conversions=").append(aw.get("conversions"));
                if (aw.get("hook") != null) sb.append(", hook=").append(aw.get("hook"));
                if (aw.get("visualStyle") != null) sb.append(", style=").append(aw.get("visualStyle"));
                sb.append("}");
            }
            sb.append("]");
        }
        return sb.toString();
    }

    /**
     * Smarter deterministic budget split that avoids spreading budget too thin.
     * <p>
     * Rules:
     * <ul>
     *   <li>LOW budget (&lt; $500): ALL on Meta — single-channel focus</li>
     *   <li>MEDIUM budget ($500–$2000): Meta primary (65%) + Google (35%) — two channels max</li>
     *   <li>HIGH budget (&gt; $2000): Meta (50%) + Google (30%) + reserve 20% for TikTok only if metrics show it works</li>
     *   <li>If historical metrics show a platform with ROAS &gt;= 2.0, shift more budget there</li>
     *   <li>TikTok/YouTube get $0 unless there is a proven signal or budget is HIGH</li>
     * </ul>
     */
    private Map<String, BigDecimal> deterministicSplit(BigDecimal budget, List<Map<String, Object>> metrics) {
        boolean hasTiktokSignal = false;
        boolean hasGoogleSignal = false;
        boolean hasMetaSignal = false;
        if (metrics != null && !metrics.isEmpty()) {
            for (Map<String, Object> m : metrics) {
                String platform = String.valueOf(m.get("platform")).toLowerCase();
                double roas = Double.parseDouble(String.valueOf(m.get("avgRoas")));
                if ("tiktok".equals(platform) && roas >= 2.0) hasTiktokSignal = true;
                if ("google".equals(platform) && roas >= 2.0) hasGoogleSignal = true;
                if ("meta".equals(platform) && roas >= 2.0) hasMetaSignal = true;
            }
        }

        // LOW budget: single channel
        if (budget.compareTo(new BigDecimal("500")) < 0) {
            return Map.of("meta", budget, "google", BigDecimal.ZERO, "tiktok", BigDecimal.ZERO, "youtube", BigDecimal.ZERO);
        }

        // MEDIUM budget: two channels max (Meta + Google)
        if (budget.compareTo(new BigDecimal("2000")) <= 0) {
            BigDecimal metaShare = hasGoogleSignal && !hasMetaSignal
                    ? budget.multiply(new BigDecimal("0.35"))
                    : budget.multiply(new BigDecimal("0.65"));
            BigDecimal googleShare = budget.subtract(metaShare);
            return Map.of("meta", metaShare, "google", googleShare, "tiktok", BigDecimal.ZERO, "youtube", BigDecimal.ZERO);
        }

        // HIGH budget: up to three channels, TikTok only with signal
        BigDecimal tiktokShare = hasTiktokSignal
                ? budget.multiply(new BigDecimal("0.15"))
                : BigDecimal.ZERO;
        BigDecimal remaining = budget.subtract(tiktokShare);
        BigDecimal metaShare = remaining.multiply(new BigDecimal("0.60")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal googleShare = remaining.subtract(metaShare);
        return Map.of("meta", metaShare, "google", googleShare, "tiktok", tiktokShare, "youtube", BigDecimal.ZERO);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOpenAi(String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing");
        String json = om.writeValueAsString(Map.of("model", model, "response_format", Map.of("type", "json_object"), "messages", List.of(Map.of("role", "system", "content", "You are a Senior Marketing Consultant and Growth Strategist. You produce detailed, business-specific marketing playbooks in strict JSON format. Never return generic template advice."), Map.of("role", "user", "content", prompt))));
        Request req = new Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization", "Bearer " + apiKey).post(RequestBody.create(json, MediaType.get("application/json"))).build();
        for (int i = 0; i < 4; i++) {
            try (Response r = client.newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    String responseBody = r.body().string();
                    log.info("OpenAI raw response length={}", responseBody.length());
                    Map<String, Object> root = om.readValue(responseBody, new TypeReference<>() {
                    });
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new RuntimeException("OpenAI returned empty choices");
                    }
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    if (message == null) {
                        throw new RuntimeException("OpenAI returned null message in first choice");
                    }
                    String content = (String) message.get("content");
                    if (content == null || content.isBlank()) {
                        throw new RuntimeException("OpenAI returned empty content");
                    }
                    log.info("OpenAI content parsed, length={}", content.length());
                    return om.readValue(content, new TypeReference<>() {
                    });
                }
                if (r.code() == 429 || r.code() >= 500) {
                    log.warn("OpenAI retryable error code={}, attempt={}", r.code(), i);
                    Thread.sleep((long) (Math.pow(2, i) * 200 + Math.random() * 100));
                    continue;
                }
                String errorBody = r.body() != null ? r.body().string() : "no body";
                log.error("OpenAI failed code={} body={}", r.code(), errorBody);
                throw new RuntimeException("OpenAI failed code=" + r.code() + " body=" + errorBody);
            } catch (java.net.SocketTimeoutException e) {
                log.warn("OpenAI timeout attempt={}", i);
                Thread.sleep((long) (Math.pow(2, i) * 200));
            }
        }
        throw new RuntimeException("OpenAI retries exhausted");
    }

    private Map<String, Object> fallback(UUID requestId, Map<String, BigDecimal> split,
                                         StrategyTemplateEntity template, int confidence,
                                         List<Map<String, Object>> decisionPath,
                                         Map<String, Object> business, boolean coldStart) {
        String bName = safeStr(business.get("businessName"));
        String industry = safeStr(business.get("industry"));
        String product = safeStr(business.get("product"));
        String audience = safeStr(business.get("targetAudience"));
        String priceRange = safeStr(business.get("priceRange"));
        String website = safeStr(business.get("websiteUrl"));
        boolean isOnline = website != null && !website.isBlank();

        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("requestId", requestId.toString());
        fb.put("strategyVersion", "v2-fallback");

        // --- existing required fields ---
        fb.put("platformBudgetSplit", split);
        BigDecimal metaDailyBudget = split.getOrDefault("meta", BigDecimal.ZERO)
                .divide(new BigDecimal("30"), java.math.RoundingMode.HALF_UP);
        fb.put("campaignPlan", List.of(
                Map.of("platform", "meta", "dailyBudget", metaDailyBudget,
                        "objective", "awareness", "targeting", audience.isBlank() ? "broad" : audience,
                        "creativeHook", "Introduce " + bName + " to new audiences")));
        fb.put("funnelStrategy", "Conservative top-of-funnel awareness for " + bName
                + " in " + industry + " using template " + template.getTemplateKey());
        fb.put("expectedCPL", coldStart ? "Unknown — insufficient data for estimation" : "Conservative range");
        fb.put("expectedROAS", coldStart ? "Unknown — insufficient data for estimation" : "Conservative range");
        fb.put("reasoning", "Deterministic fallback strategy for " + bName + " (" + industry
                + ") using template=" + template.getTemplateKey() + " confidence=" + confidence
                + ". OpenAI was unavailable; this plan is generated from business profile and strategy intelligence rules.");
        fb.put("assumptions", List.of("No model output available", "DecisionPath=" + decisionPath,
                "Using conservative defaults for " + industry));

        // --- consultant-style sections ---
        fb.put("businessSnapshot", Map.of("summary",
                bName + " operates in the " + industry + " industry"
                        + (product.isBlank() ? "" : " selling " + product)
                        + (priceRange.isBlank() ? "" : " at " + priceRange + " price point")
                        + (isOnline ? " with an online presence at " + website : " as an offline business")
                        + "."));

        fb.put("marketAnalysis", Map.of(
                "competitionContext", "Assume moderate competition in " + industry + ". Validate with market research.",
                "demandContext", "Standard demand patterns expected for " + industry + ".",
                "buyingBehavior", audience.isBlank() ? "General consumer buying patterns." : "Target: " + audience + ".",
                "seasonalConsiderations", "Review seasonal peaks for " + industry + " and adjust budget accordingly."));

        fb.put("customerPersona", Map.of(
                "primaryBuyer", audience.isBlank() ? "To be determined through initial testing" : audience,
                "motivations", "Quality, value, and trust in " + industry + " products.",
                "painPoints", "Finding reliable " + industry + " providers; price transparency.",
                "triggers", "Promotions, social proof, limited-time offers."));

        fb.put("whyThisStrategy", Map.of(
                "whyChosenPlatforms", "Template " + template.getTemplateKey() + " recommends channel mix: " + template.getChannelMixJson(),
                "whyOthersLowerPriority", "Budget and business profile indicate focusing on primary channels first."));

        fb.put("platformStrategy", List.of(Map.of(
                "platform", "meta", "whyChosen", "Broad reach and visual format suits " + industry,
                "campaignObjective", "awareness", "budgetAllocation", split.getOrDefault("meta", BigDecimal.ZERO),
                "duration", "30 days", "audienceType", audience.isBlank() ? "broad" : audience,
                "creativeFormat", "image + short video", "successMetric", "CTR > 1%")));

        Map<String, Object> campaignArch = new LinkedHashMap<>();
        campaignArch.put("platform", "meta");
        campaignArch.put("campaignThemes", List.of("Brand Introduction", "Product Showcase"));
        campaignArch.put("adGroupLogic", "Split by audience segment");
        campaignArch.put("keywordDirection", null);
        campaignArch.put("retargetingLogic", isOnline ? "Retarget website visitors after 3 days" : "Retarget video viewers");
        fb.put("campaignArchitecture", List.of(campaignArch));

        fb.put("creativeStrategy", Map.of(
                "creativeAngles", List.of("Trust & quality", "Value proposition", "Social proof"),
                "hooks", List.of("Discover " + bName, "Why customers choose " + bName),
                "messagingStyle", "Direct, benefit-focused, simple language",
                "contentTypes", List.of("Static image", "Short-form video (15s)", "Carousel"),
                "examples", List.of("Product hero shot on clean background",
                        "Customer testimonial video", "Before/after or comparison carousel")));

        fb.put("creativesNeeded", List.of(
                Map.of("type", "image", "description", "Product hero image", "quantity", 3, "specs", "1080x1080 and 1080x1920"),
                Map.of("type", "video", "description", "15-second product showcase", "quantity", 2, "specs", "9:16 vertical, subtitled"),
                Map.of("type", "carousel", "description", "Product range showcase", "quantity", 1, "specs", "Up to 10 slides, 1080x1080")));

        fb.put("executionRoadmap", Map.of(
                "week1", "Set up tracking, create ad accounts, launch 2-3 awareness campaigns on Meta with broad targeting.",
                "week2", "Review initial CTR and CPM data. Pause underperforming creatives. Test 2 new hooks.",
                "week3", "Narrow audiences based on week 1-2 data. Add retargeting campaign for engaged users.",
                "week4", "Evaluate full funnel performance. Scale winning ad sets by 20%. Plan next month.",
                "launchFirst", "Meta awareness campaign with best creative assets",
                "measureFirst", "CTR, CPM, and link clicks in first 72 hours",
                "changeAfter", "Pause creatives with CTR < 0.8% after 1000 impressions"));

        fb.put("setupChecklist", List.of(
                Map.of("item", "Meta Business Manager", "details", "Create or verify business account", "priority", "HIGH"),
                Map.of("item", "Meta Pixel", "details", isOnline ? "Install pixel on " + website : "Set up offline event tracking", "priority", "HIGH"),
                Map.of("item", "Conversion Events", "details", "Configure Purchase/Lead events", "priority", "HIGH"),
                Map.of("item", "Landing Page", "details", isOnline ? "Ensure landing page loads < 3s with clear CTA" : "Set up simple landing page or WhatsApp link", "priority", "HIGH"),
                Map.of("item", "Google Business Profile", "details", "Claim and optimize listing", "priority", "MEDIUM"),
                Map.of("item", "Contact Flow", "details", "Ensure WhatsApp/phone/email response under 5 min", "priority", "MEDIUM")));

        fb.put("landingPageRecommendations", Map.of(
                "pageType", isOnline ? "Product/service landing page" : "Simple one-page with contact form or WhatsApp button",
                "conversionElements", List.of("Clear headline", "Product/service images", "Price or offer", "CTA button", "Social proof", "Contact method"),
                "mustHave", List.of("Mobile responsive", "Load time < 3 seconds", "Single clear CTA")));

        fb.put("offerStrategy", Map.of(
                "primaryOffer", "Introductory offer or first-purchase discount for new customers",
                "cta", "Shop Now / Get a Quote / Message Us",
                "urgencyTactic", "Limited-time introductory pricing or early-bird bonus"));

        fb.put("measurementPlan", Map.of(
                "kpiTargets", Map.of("CTR", "> 1.0%", "CPC", "< industry average", "ROAS", "target 2.0x after learning phase"),
                "evaluationWindow", "7-day rolling window, full review at day 14 and day 30",
                "stopLossRules", List.of("Pause ad set if spend > 3x target CPA with 0 conversions",
                        "Pause creative if CTR < 0.5% after 2000 impressions"),
                "scalingRules", List.of("Increase budget 20% on ad sets with ROAS > 2.0x for 3+ consecutive days",
                        "Duplicate winning ad sets to new audience segments")));

        fb.put("risksAndMitigations", List.of(
                Map.of("risk", "Low initial conversion rate due to cold audience",
                        "mitigation", "Focus week 1-2 on data collection, not conversions. Use engagement objectives."),
                Map.of("risk", "Ad fatigue after 2 weeks",
                        "mitigation", "Prepare 2-3 creative variations per ad set. Rotate every 7-10 days."),
                Map.of("risk", "Budget wasted on wrong audience",
                        "mitigation", "Start broad, then narrow based on data. Review audience insights at day 7.")));

        fb.put("first14DaysLearningPlan", Map.of(
                "dataToCollect", List.of("CTR by creative", "CPM by audience", "Link click-through rate",
                        "Landing page bounce rate", "Cost per result by ad set"),
                "decisionsToDefer", List.of("Final audience selection until day 10+",
                        "Budget scaling until positive signal confirmed",
                        "Channel expansion until primary channel is optimized"),
                "testingPlan", "Run 3 creatives x 2 audiences = 6 ad sets. Evaluate after 1000 impressions each.",
                "expectedOutcome", "By day 14: identify 1-2 winning creatives and 1 best audience segment."));

        fb.put("humanReadablePlanMarkdown", buildFallbackMarkdown(bName, industry, product, audience,
                priceRange, isOnline, template.getTemplateKey(), confidence, coldStart));

        return fb;
    }

    /**
     * Generates a human-readable markdown strategy plan for fallback responses.
     */
    private String buildFallbackMarkdown(String bName, String industry, String product,
                                         String audience, String priceRange, boolean isOnline,
                                         String templateKey, int confidence, boolean coldStart) {
        StringBuilder md = new StringBuilder();
        md.append("# Marketing Strategy for ").append(bName).append("\n\n");
        md.append("## Business Overview\n");
        md.append(bName).append(" operates in the **").append(industry).append("** industry");
        if (!product.isBlank()) md.append(", selling **").append(product).append("**");
        if (!priceRange.isBlank()) md.append(" at a **").append(priceRange).append("** price point");
        md.append(".\n\n");

        if (coldStart) {
            md.append("## ⚠️ New Business — Learning Phase\n");
            md.append("Since this is a new business with no advertising history, the first 14 days focus on **data collection and learning**, not immediate sales.\n\n");
        }

        md.append("## Recommended Approach\n");
        md.append("Based on our analysis (strategy template: `").append(templateKey).append("`, confidence: ").append(confidence).append("/100), ");
        md.append("we recommend starting with **Meta (Facebook/Instagram)** as the primary channel.\n\n");

        md.append("## Week-by-Week Plan\n");
        md.append("- **Week 1:** Set up tracking, launch awareness campaigns, test 3 creative variations.\n");
        md.append("- **Week 2:** Review data, pause underperformers, test new hooks.\n");
        md.append("- **Week 3:** Add retargeting, narrow audiences based on learnings.\n");
        md.append("- **Week 4:** Evaluate performance, scale winners, plan month 2.\n\n");

        md.append("## What You Need to Prepare\n");
        md.append("- 3 product/service images (1080x1080)\n");
        md.append("- 2 short videos (15 seconds, vertical)\n");
        md.append("- Clear offer or promotion\n");
        if (!isOnline) md.append("- Landing page or WhatsApp business link\n");
        md.append("\n");

        md.append("## Important Reminders\n");
        md.append("- Do NOT judge results before day 14.\n");
        md.append("- Pause any ad with CTR below 0.5% after 2000 impressions.\n");
        md.append("- Scale only what is proven — increase budget 20% at a time.\n\n");

        md.append("*This plan was generated deterministically because the AI model was unavailable. ");
        md.append("It uses conservative defaults based on your business profile and our strategy rules.*\n");
        return md.toString();
    }

    private String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    public Map<String, Object> normalizeOrFallback(Map<String, Object> llmResp, UUID requestId,
                                                   Map<String, BigDecimal> split,
                                                   Map<String, Object> business,
                                                   StrategyTemplateEntity template,
                                                   boolean coldStart) {
        if (llmResp == null || llmResp.isEmpty()) {
            return fallback(requestId, split, template, 0, List.of(), business, coldStart);
        }
        if (llmResp.get("platformBudgetSplit") == null || llmResp.get("campaignPlan") == null) {
            return fallback(requestId, split, template, 0, List.of(Map.of("note", "Incomplete model schema")), business, coldStart);
        }
        Map<String, Object> normalized = new HashMap<>(llmResp);
        normalized.put("requestId", requestId.toString());
        normalized.putIfAbsent("strategyVersion", "v2");
        normalized.putIfAbsent("funnelStrategy", "Conservative optimization based on available data");
        normalized.putIfAbsent("expectedCPL", "Unknown");
        normalized.putIfAbsent("expectedROAS", "Unknown");
        normalized.putIfAbsent("reasoning", "Generated strategy with guarded defaults");
        normalized.putIfAbsent("assumptions", List.of());
        return normalized;
    }

    /**
     * Legacy overload for backward compatibility with existing tests.
     */
    public Map<String, Object> normalizeOrFallback(Map<String, Object> llmResp, UUID requestId, Map<String, BigDecimal> split) {
        if (llmResp == null || llmResp.isEmpty()) {
            return Map.of("requestId", requestId.toString(), "strategyVersion", "v2", "platformBudgetSplit", split,
                    "campaignPlan", List.of(), "funnelStrategy", "Conservative optimization based on available data",
                    "expectedCPL", "Unknown", "expectedROAS", "Unknown", "reasoning", "Generated strategy with guarded defaults",
                    "assumptions", List.of());
        }
        if (llmResp.get("platformBudgetSplit") == null || llmResp.get("campaignPlan") == null) {
            return Map.of("requestId", requestId.toString(), "strategyVersion", "v2", "platformBudgetSplit", split,
                    "campaignPlan", List.of(), "funnelStrategy", "Conservative optimization based on available data",
                    "expectedCPL", "Unknown", "expectedROAS", "Unknown", "reasoning", "Generated strategy with guarded defaults",
                    "assumptions", List.of("Incomplete model schema"));
        }
        Map<String, Object> normalized = new HashMap<>(llmResp);
        normalized.put("requestId", requestId.toString());
        normalized.putIfAbsent("strategyVersion", "v2");
        normalized.putIfAbsent("funnelStrategy", "Conservative optimization based on available data");
        normalized.putIfAbsent("expectedCPL", "Unknown");
        normalized.putIfAbsent("expectedROAS", "Unknown");
        normalized.putIfAbsent("reasoning", "Generated strategy with guarded defaults");
        normalized.putIfAbsent("assumptions", List.of());
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private StrategyResponse toResponse(Map<String, Object> m) {
        UUID responseRequestId = resolveRequestId(m);
        String version = Optional.ofNullable(m.get("strategyVersion"))
                .map(Object::toString)
                .filter(v -> !v.isBlank())
                .orElse("v2");
        return new StrategyResponse(
                responseRequestId,
                version,
                om.convertValue(m.get("platformBudgetSplit"), new TypeReference<>() {
                }),
                om.convertValue(m.get("campaignPlan"), new TypeReference<>() {
                }),
                (String) m.get("funnelStrategy"),
                (String) m.get("expectedCPL"),
                (String) m.get("expectedROAS"),
                (String) m.get("reasoning"),
                om.convertValue(m.get("assumptions"), new TypeReference<>() {
                }),
                // --- consultant-style playbook sections (nullable) ---
                asMap(m.get("businessSnapshot")),
                asMap(m.get("marketAnalysis")),
                asMap(m.get("customerPersona")),
                asMap(m.get("whyThisStrategy")),
                asList(m.get("platformStrategy")),
                asList(m.get("campaignArchitecture")),
                asMap(m.get("creativeStrategy")),
                asList(m.get("creativesNeeded")),
                asMap(m.get("executionRoadmap")),
                asList(m.get("setupChecklist")),
                asMap(m.get("landingPageRecommendations")),
                asMap(m.get("offerStrategy")),
                asMap(m.get("measurementPlan")),
                asList(m.get("risksAndMitigations")),
                asMap(m.get("first14DaysLearningPlan")),
                m.get("humanReadablePlanMarkdown") != null ? m.get("humanReadablePlanMarkdown").toString() : null
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o == null) return null;
        if (o instanceof Map) return (Map<String, Object>) o;
        return om.convertValue(o, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object o) {
        if (o == null) return null;
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return om.convertValue(o, new TypeReference<>() {
        });
    }

    /**
     * Anti-generic validation: detects if the LLM output is too template-like or generic.
     * <p>
     * Checks that the output meaningfully references business-specific facts and includes
     * the required consultant-style sections. Returns a list of violation descriptions.
     * An empty list means the output passed validation.
     * <p>
     * If validation fails, the caller should retry once with a stronger instruction or
     * enrich the response with deterministic fallback sections.
     */
    public List<String> validateNotGeneric(Map<String, Object> llmResp, Map<String, Object> business) {
        List<String> violations = new ArrayList<>();
        if (llmResp == null || llmResp.isEmpty()) {
            violations.add("Response is null or empty");
            return violations;
        }

        String businessName = safeStr(business.get("businessName")).toLowerCase();
        String industry = safeStr(business.get("industry")).toLowerCase();
        String product = safeStr(business.get("product")).toLowerCase();
        String location = safeStr(business.get("location")).toLowerCase();

        // Serialize the entire response to search for business-specific references
        String fullJson = llmResp.toString().toLowerCase();

        // Check that business name or industry appears meaningfully somewhere
        if (!businessName.isBlank() && !fullJson.contains(businessName)) {
            violations.add("Business name '" + business.get("businessName") + "' not referenced in output");
        }
        if (!industry.isBlank() && !fullJson.contains(industry)) {
            violations.add("Industry '" + business.get("industry") + "' not referenced in output");
        }

        // Check product is referenced when available
        if (!product.isBlank() && !fullJson.contains(product)) {
            violations.add("Product '" + business.get("product") + "' not referenced in output");
        }

        // Check location is referenced when available (business-specificity signal)
        if (!location.isBlank() && !fullJson.contains(location)) {
            violations.add("Location '" + business.get("location") + "' not referenced in output");
        }

        // Check platform choice explanation exists
        if (llmResp.get("whyThisStrategy") == null && llmResp.get("platformStrategy") == null) {
            violations.add("No platform choice explanation (whyThisStrategy or platformStrategy missing)");
        }

        // Check execution roadmap exists
        if (llmResp.get("executionRoadmap") == null) {
            violations.add("No executionRoadmap section");
        }

        // Check setup checklist exists
        if (llmResp.get("setupChecklist") == null) {
            violations.add("No setupChecklist section");
        }

        // Check creative strategy exists
        if (llmResp.get("creativeStrategy") == null) {
            violations.add("No creativeStrategy section");
        }

        // Check humanReadablePlanMarkdown has real depth (not just a two-line summary)
        Object mdObj = llmResp.get("humanReadablePlanMarkdown");
        if (mdObj == null) {
            violations.add("humanReadablePlanMarkdown is missing");
        } else {
            String md = mdObj.toString();
            if (md.length() < 500) {
                violations.add("humanReadablePlanMarkdown is too short (" + md.length() + " chars) — must be a full consultant memo");
            }
        }

        // Detect boilerplate / vague filler phrases that indicate generic output
        List<String> boilerplatePhrases = List.of(
                "optimize regularly", "monitor and adjust", "adjust as needed",
                "track performance", "review results periodically",
                "leverage social media", "utilize digital marketing"
        );
        long boilerplateCount = boilerplatePhrases.stream().filter(fullJson::contains).count();
        if (boilerplateCount >= 3) {
            violations.add("Output contains " + boilerplateCount + " generic boilerplate phrases — too template-like");
        }

        // Check it's not just a flat budget template (only has the old fields and nothing else)
        long consultantFieldCount = List.of(
                "businessSnapshot", "marketAnalysis", "customerPersona", "whyThisStrategy",
                "platformStrategy", "campaignArchitecture", "creativeStrategy", "creativesNeeded",
                "executionRoadmap", "setupChecklist", "landingPageRecommendations", "offerStrategy",
                "measurementPlan", "risksAndMitigations", "first14DaysLearningPlan", "humanReadablePlanMarkdown"
        ).stream().filter(k -> llmResp.get(k) != null).count();

        if (consultantFieldCount < 4) {
            violations.add("Output is a flat budget template — only " + consultantFieldCount + "/16 consultant sections present");
        }

        // Check platformStrategy entries have justification (whyChosen field must be non-empty)
        Object psObj = llmResp.get("platformStrategy");
        if (psObj instanceof List<?> psList && !psList.isEmpty()) {
            for (Object entry : psList) {
                if (entry instanceof Map<?, ?> entryMap) {
                    Object whyChosen = entryMap.get("whyChosen");
                    if (whyChosen == null || whyChosen.toString().isBlank()) {
                        violations.add("platformStrategy entry missing 'whyChosen' justification for platform: " + entryMap.get("platform"));
                    }
                }
            }
        }

        return violations;
    }

    /**
     * Enriches an existing (but too generic) LLM response with deterministic fallback
     * consultant-style sections. Preserves any sections the LLM did produce correctly,
     * only fills in what's missing.
     */
    public Map<String, Object> enrichWithFallbackSections(Map<String, Object> llmResp,
                                                          Map<String, Object> business,
                                                          StrategyTemplateEntity template,
                                                          Map<String, BigDecimal> split,
                                                          boolean coldStart) {
        Map<String, Object> enriched = new HashMap<>(llmResp);
        Map<String, Object> fb = fallback(UUID.randomUUID(), split, template, 0, List.of(), business, coldStart);
        // Only fill in missing consultant sections from the deterministic fallback
        for (String key : List.of("businessSnapshot", "marketAnalysis", "customerPersona", "whyThisStrategy",
                "platformStrategy", "campaignArchitecture", "creativeStrategy", "creativesNeeded",
                "executionRoadmap", "setupChecklist", "landingPageRecommendations", "offerStrategy",
                "measurementPlan", "risksAndMitigations", "first14DaysLearningPlan", "humanReadablePlanMarkdown")) {
            enriched.putIfAbsent(key, fb.get(key));
        }
        return enriched;
    }

    private UUID resolveRequestId(Map<String, Object> payload) {
        Object raw = payload.get("requestId");
        if (raw == null) {
            UUID generated = UUID.randomUUID();
            log.warn("LLM response missing requestId; generating {}", generated);
            return generated;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        String value = raw.toString();
        if (value.isBlank()) {
            UUID generated = UUID.randomUUID();
            log.warn("LLM response returned blank requestId; generating {}", generated);
            return generated;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            UUID generated = UUID.randomUUID();
            log.warn("LLM response returned invalid requestId [{}]; generating {}", value, generated);
            return generated;
        }
    }

    private Map<String, Object> getBusiness(UUID id) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT business_name,industry,product,target_audience,website_url,price_range,location FROM business_profile WHERE id=?")) {
            ps.setObject(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> b = new HashMap<>();
                b.put("businessName", rs.getString(1));
                b.put("industry", rs.getString(2));
                b.put("product", rs.getString(3));
                b.put("targetAudience", rs.getString(4));
                b.put("websiteUrl", rs.getString(5));
                b.put("priceRange", rs.getString(6));
                b.put("location", rs.getString(7));
                return b;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Map.of();
    }

    private List<Map<String, Object>> metrics(UUID id) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT platform,COALESCE(SUM(spend),0),COALESCE(AVG(roas),0),COALESCE(SUM(conversions),0) FROM campaign_metrics WHERE business_id=? AND recorded_at>=? GROUP BY platform")) {
            ps.setObject(1, id);
            ps.setTimestamp(2, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                out.add(Map.of("platform", rs.getString(1), "spend", rs.getBigDecimal(2), "avgRoas", rs.getBigDecimal(3), "conversions", rs.getLong(4)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private List<Map<String, Object>> winners(UUID id) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT platform,hook,angle,performance_score FROM creatives WHERE business_id=? ORDER BY performance_score DESC NULLS LAST LIMIT 3")) {
            ps.setObject(1, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                out.add(Map.of("platform", rs.getString(1), "hook", rs.getString(2), "angle", rs.getString(3), "score", rs.getBigDecimal(4)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private List<Map<String, Object>> assetWinners(UUID businessId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT cap.creative_asset_id, cap.platform, " +
                "COALESCE(SUM(cap.impressions),0), COALESCE(SUM(cap.clicks),0), " +
                "COALESCE(SUM(cap.conversions),0), COALESCE(AVG(cap.roas),0), ca.metadata_json " +
                "FROM creative_asset_performance cap JOIN creative_assets ca ON ca.id=cap.creative_asset_id " +
                "WHERE cap.business_id=? AND cap.recorded_at>=? " +
                "GROUP BY cap.creative_asset_id, cap.platform, ca.metadata_json " +
                "HAVING COALESCE(SUM(cap.impressions),0) >= 3000 AND COALESCE(AVG(cap.roas),0) >= 2.0 " +
                "ORDER BY 6 DESC LIMIT 5")) {
            ps.setObject(1, businessId);
            ps.setTimestamp(2, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("assetId", rs.getObject(1).toString());
                m.put("platform", rs.getString(2));
                m.put("impressions", rs.getLong(3));
                m.put("clicks", rs.getLong(4));
                m.put("conversions", rs.getLong(5));
                m.put("avgRoas", rs.getBigDecimal(6));
                String metaJson = rs.getString(7);
                if (metaJson != null && !metaJson.isBlank()) {
                    try {
                        Map<String, Object> meta = om.readValue(metaJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        m.put("hook", meta.get("hook"));
                        m.put("visualStyle", meta.get("visualStyle"));
                        m.put("emotionalAngle", meta.get("emotionalAngle"));
                    } catch (Exception ignored) {}
                }
                out.add(m);
            }
        } catch (Exception e) {
            log.warn("Failed to query asset winners: {}", e.getMessage());
        }
        return out;
    }

    private List<Map<String, Object>> buildWinnerInsights(List<Map<String, Object>> assetWinners) {
        List<Map<String, Object>> insights = new ArrayList<>();
        for (Map<String, Object> aw : assetWinners) {
            Map<String, Object> insight = new LinkedHashMap<>();
            insight.put("assetId", aw.get("assetId"));
            insight.put("platform", aw.get("platform"));
            insight.put("roas", aw.get("avgRoas"));
            insight.put("conversions", aw.get("conversions"));
            if (aw.get("hook") != null) insight.put("winningHook", aw.get("hook"));
            if (aw.get("visualStyle") != null) insight.put("winningStyle", aw.get("visualStyle"));
            if (aw.get("emotionalAngle") != null) insight.put("winningAngle", aw.get("emotionalAngle"));
            insights.add(insight);
        }
        return insights;
    }

    private List<String> buildCreativeMoves(List<Map<String, Object>> assetWinners) {
        List<String> moves = new ArrayList<>();
        if (!assetWinners.isEmpty()) {
            moves.add("Scale budget on " + assetWinners.size() + " winning creative asset(s)");
            moves.add("Create iterations of top-performing asset concepts using /generate/creative-assets/from-winner");
            Map<String, Object> best = assetWinners.get(0);
            if (best.get("visualStyle") != null) {
                moves.add("Double down on '" + best.get("visualStyle") + "' visual style — it's producing the best ROAS");
            }
            if (best.get("hook") != null) {
                moves.add("Test variations of winning hook pattern: '" + best.get("hook") + "'");
            }
            moves.add("Pause or rework assets not meeting ROAS threshold of 2.0");
        }
        return moves;
    }

    private List<String> trendsByIndustry(String industry) {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT keyword FROM trends WHERE (industry=? OR industry IS NULL) AND captured_at>=? ORDER BY captured_at DESC LIMIT 10")) {
            ps.setString(1, industry);
            ps.setTimestamp(2, Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS)));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getString(1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private StrategyRunIntelEntity latestIntel(UUID businessId) {
        List<StrategyRunIntelEntity> rows = intelRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> performanceForRequest(UUID businessId, UUID requestId) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COALESCE(AVG(roas),0), COALESCE(SUM(conversions),0) FROM campaign_metrics WHERE business_id=? AND recorded_at>=?")) {
            ps.setObject(1, businessId);
            ps.setTimestamp(2, Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return Map.of("roas", rs.getBigDecimal(1), "conversions", rs.getLong(2), "requestId", requestId.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Map.of();
    }

    private double metricRoas(List<Map<String, Object>> metrics) {
        return metrics.stream().mapToDouble(m -> Double.parseDouble(String.valueOf(m.get("avgRoas")))).average().orElse(0.0);
    }

    private long metricConversions(List<Map<String, Object>> metrics) {
        return metrics.stream().mapToLong(m -> Long.parseLong(String.valueOf(m.get("conversions")))).sum();
    }

    private void saveIntel(UUID requestId, StrategyRequest req, String key, List<Map<String, Object>> decisionPath, ConfidenceScorer.ConfidenceResult confidence, Map<String, Object> similarityMatch) {
        try {
            StrategyRunIntelEntity e = new StrategyRunIntelEntity();
            e.setId(UUID.randomUUID());
            e.setRequestId(requestId);
            e.setBusinessId(req.businessId());
            e.setObjective(req.objective());
            e.setMonthlyBudget(req.monthlyBudget());
            e.setChosenTemplateKey(key);
            e.setDecisionPathJson(om.writeValueAsString(decisionPath));
            e.setConfidenceScore(confidence.confidenceScore());
            e.setScoreBreakdownJson(om.writeValueAsString(confidence.breakdown()));
            e.setSimilarityMatchJson(similarityMatch == null ? null : om.writeValueAsString(similarityMatch));
            e.setCreatedAt(Instant.now());
            intelRepository.save(e);
        } catch (Exception e) {
            log.error("intel save error", e);
        }
    }

    private void saveHistory(UUID requestId, StrategyRequest req, String prompt, Map<String, Object> response, String status, String code, String error, Long latency) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO strategy_history(id,request_id,business_id,objective,monthly_budget,trends_json,prompt_version,model_name,request_json,response_json,status,error_code,error_message,openai_latency_ms,created_at) VALUES (?,?,?,?,?,?::jsonb,?,?,?::jsonb,?::jsonb,?,?,?,?,?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, requestId);
            ps.setObject(3, req.businessId());
            ps.setString(4, req.objective());
            ps.setBigDecimal(5, req.monthlyBudget());
            ps.setString(6, om.writeValueAsString(req.trends() == null ? List.of() : req.trends()));
            ps.setString(7, "v2");
            ps.setString(8, model);
            ps.setString(9, om.writeValueAsString(req));
            ps.setString(10, om.writeValueAsString(response));
            ps.setString(11, status);
            ps.setString(12, code);
            ps.setString(13, error);
            ps.setLong(14, latency);
            ps.setTimestamp(15, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("history save error", e);
        }
    }

    public List<StrategyIntelSummary> intelHistory(UUID businessId, int limit) {
        return intelRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream().limit(limit)
                .map(i -> new StrategyIntelSummary(i.getRequestId(), i.getChosenTemplateKey(), i.getConfidenceScore(), i.getMonthlyBudget(), i.getObjective(), i.getCreatedAt()))
                .toList();
    }

    public List<HistorySummary> history(UUID businessId, int limit) {
        List<HistorySummary> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT request_id,objective,monthly_budget,status,created_at FROM strategy_history WHERE business_id=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setObject(1, businessId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                out.add(new HistorySummary((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4), rs.getTimestamp(5).toInstant()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }
}
