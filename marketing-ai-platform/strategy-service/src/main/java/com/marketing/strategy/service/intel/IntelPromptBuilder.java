package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Builds a structured, consultant-grade LLM prompt that instructs the model to behave as a
 * "Senior Marketing Consultant and Growth Strategist".
 * <p>
 * The prompt injects five clearly separated context sections:
 * <ol>
 *   <li><b>Business Memory</b> — business_profile facts</li>
 *   <li><b>Performance Memory</b> — aggregated last-30-day campaign metrics (if available)</li>
 *   <li><b>Creative Winners</b> — top performing creatives (if available)</li>
 *   <li><b>Trend Signals</b> — latest 7-day trend data (if available)</li>
 *   <li><b>Strategy Intelligence</b> — deterministic template key, confidence, decision path</li>
 * </ol>
 * <p>
 * The deterministic Strategy Intelligence Engine remains the source of truth.
 * The LLM must explain and elaborate — never override the chosen template or invent fake data.
 */
@Component
public class IntelPromptBuilder {

    public String build(Map<String, Object> business, String objective, Object budget,
                        StrategyTemplateEntity template, String perfSummary,
                        List<String> trends, int confidenceScore, boolean coldStart) {
        StringBuilder sb = new StringBuilder(4096);

        // ─── ROLE ───────────────────────────────────────────────────────
        sb.append("You are a Senior Marketing Consultant and Growth Strategist.\n");
        sb.append("You speak in clear, actionable, business-friendly language that a non-technical or ");
        sb.append("uneducated business owner can directly follow and implement without outside help.\n\n");

        // ─── STRICT RULES ───────────────────────────────────────────────
        sb.append("=== STRICT RULES ===\n");
        sb.append("1. Every recommendation MUST directly reference the business facts below (name, industry, product, location, price range, target audience, website presence).\n");
        sb.append("2. Do NOT give generic or reusable template advice. The output must be unique to THIS business.\n");
        sb.append("3. Do NOT invent performance history that does not exist. Use only the data provided.\n");
        sb.append("4. Do NOT override the chosen strategy template. Explain and elaborate on it.\n");
        sb.append("5. Do NOT use vague phrases like 'optimize regularly', 'monitor performance', or 'adjust as needed' without specific thresholds, timelines, or actions.\n");
        sb.append("6. Do NOT create a generic budget plan that could fit any business.\n");
        sb.append("7. Provide step-by-step implementation details a business owner can act on immediately.\n");
        sb.append("8. Include week-by-week execution plan, creative guidance, setup checklist, measurement plan, and risk analysis.\n");
        sb.append("9. Return ONLY a valid JSON object. No markdown fences, no commentary outside JSON.\n\n");

        // ─── A) BUSINESS MEMORY ─────────────────────────────────────────
        sb.append("=== A) BUSINESS MEMORY ===\n");
        sb.append("businessName: ").append(safeStr(business.get("businessName"))).append("\n");
        sb.append("industry: ").append(safeStr(business.get("industry"))).append("\n");
        sb.append("product: ").append(safeStr(business.get("product"))).append("\n");
        sb.append("priceRange: ").append(safeStr(business.get("priceRange"))).append("\n");
        sb.append("location: ").append(safeStr(business.get("location"))).append("\n");
        sb.append("targetAudience: ").append(safeStr(business.get("targetAudience"))).append("\n");
        String website = safeStr(business.get("websiteUrl"));
        sb.append("websiteUrl: ").append(website.isBlank() ? "NONE (offline business)" : website).append("\n");
        sb.append("objective: ").append(objective != null ? objective : "sales").append("\n");
        sb.append("monthlyBudget: ").append(budget).append("\n\n");

        // ─── B) PERFORMANCE MEMORY ──────────────────────────────────────
        sb.append("=== B) PERFORMANCE MEMORY (last 30 days) ===\n");
        if (coldStart) {
            sb.append("NO HISTORICAL PERFORMANCE DATA. This is a brand-new business with ZERO ad history.\n");
        } else {
            sb.append(perfSummary).append("\n");
        }
        sb.append("\n");

        // ─── C) CREATIVE WINNERS ────────────────────────────────────────
        sb.append("=== C) CREATIVE WINNERS ===\n");
        if (coldStart || perfSummary.contains("winners=[]")) {
            sb.append("No creative winners yet. Recommend initial creative testing strategy.\n");
        } else {
            sb.append(perfSummary).append("\n");
        }
        sb.append("\n");

        // ─── D) TREND SIGNALS (last 7 days) ────────────────────────────
        sb.append("=== D) TREND SIGNALS (last 7 days) ===\n");
        if (trends == null || trends.isEmpty()) {
            sb.append("No recent trends available.\n");
        } else {
            for (String trend : trends) {
                sb.append("- ").append(trend).append("\n");
            }
        }
        sb.append("\n");

        // ─── E) STRATEGY INTELLIGENCE (deterministic — source of truth) ─
        sb.append("=== E) STRATEGY INTELLIGENCE (deterministic source of truth) ===\n");
        sb.append("chosenTemplateKey: ").append(template.getTemplateKey()).append("\n");
        sb.append("templateName: ").append(template.getName()).append("\n");
        sb.append("templateDescription: ").append(template.getDescription()).append("\n");
        sb.append("channelMix: ").append(template.getChannelMixJson()).append("\n");
        sb.append("campaignStructure: ").append(template.getCampaignStructureJson()).append("\n");
        sb.append("creativeAngles: ").append(template.getCreativeAnglesJson()).append("\n");
        sb.append("kpiTargets: ").append(template.getKpiTargetsJson()).append("\n");
        sb.append("riskFactors: ").append(template.getRiskFactorsJson()).append("\n");
        sb.append("confidenceScore: ").append(confidenceScore).append("/100\n\n");

        // ─── COLD-START INSTRUCTIONS ────────────────────────────────────
        if (coldStart) {
            sb.append("=== COLD-START INSTRUCTIONS ===\n");
            sb.append("This business has NO historical ad performance, NO creative winners, and NO conversion data.\n");
            sb.append("You MUST:\n");
            sb.append("- Use conservative budget ranges and realistic expectations.\n");
            sb.append("- Focus the first 14 days on learning: broad targeting, A/B testing creatives, collecting pixel data.\n");
            sb.append("- Include a detailed 'first14DaysLearningPlan' section.\n");
            sb.append("- Recommend structured testing before scaling.\n");
            sb.append("- Set expectations clearly: 'Week 1-2 is about data, not sales.'\n");
            sb.append("- Do NOT predict specific ROAS or CPL numbers — use ranges with caveats.\n\n");
        }

        // ─── OUTPUT SCHEMA ──────────────────────────────────────────────
        sb.append("=== REQUIRED JSON OUTPUT SCHEMA ===\n");
        sb.append("Return a single JSON object with ALL of these top-level keys:\n\n");

        sb.append("1. \"platformBudgetSplit\": { \"meta\": number, \"google\": number, \"tiktok\": number, \"youtube\": number }\n");
        sb.append("2. \"campaignPlan\": [ { \"platform\": string, \"dailyBudget\": number, \"objective\": string, \"targeting\": string, \"creativeHook\": string } ]\n");
        sb.append("3. \"funnelStrategy\": string — describe the funnel approach\n");
        sb.append("4. \"expectedCPL\": string — cost per lead estimate (range for cold start)\n");
        sb.append("5. \"expectedROAS\": string — return on ad spend estimate (range for cold start)\n");
        sb.append("6. \"reasoning\": string — explain the overall strategy in 2-3 sentences\n");
        sb.append("7. \"assumptions\": [ string ] — list key assumptions\n\n");

        sb.append("8. \"businessSnapshot\": { \"summary\": string } — 2-3 sentence summary using actual business facts\n");
        sb.append("9. \"marketAnalysis\": { \"competitionContext\": string, \"demandContext\": string, \"buyingBehavior\": string, \"seasonalConsiderations\": string }\n");
        sb.append("10. \"customerPersona\": { \"primaryBuyer\": string, \"motivations\": string, \"painPoints\": string, \"triggers\": string }\n");
        sb.append("11. \"whyThisStrategy\": { \"whyChosenPlatforms\": string, \"whyOthersLowerPriority\": string }\n");
        sb.append("12. \"platformStrategy\": [ { \"platform\": string, \"whyChosen\": string, \"campaignObjective\": string, \"budgetAllocation\": number, \"duration\": string, \"audienceType\": string, \"creativeFormat\": string, \"successMetric\": string } ]\n");
        sb.append("13. \"campaignArchitecture\": [ { \"platform\": string, \"campaignThemes\": [string], \"adGroupLogic\": string, \"keywordDirection\": string|null, \"retargetingLogic\": string|null } ]\n");
        sb.append("14. \"creativeStrategy\": { \"creativeAngles\": [string], \"hooks\": [string], \"messagingStyle\": string, \"contentTypes\": [string], \"examples\": [string] }\n");
        sb.append("15. \"creativesNeeded\": [ { \"type\": string, \"description\": string, \"quantity\": number, \"specs\": string } ]\n");
        sb.append("16. \"executionRoadmap\": { \"week1\": string, \"week2\": string, \"week3\": string, \"week4\": string, \"launchFirst\": string, \"measureFirst\": string, \"changeAfter\": string }\n");
        sb.append("17. \"setupChecklist\": [ { \"item\": string, \"details\": string, \"priority\": string } ]\n");
        sb.append("18. \"landingPageRecommendations\": { \"pageType\": string, \"conversionElements\": [string], \"mustHave\": [string] }\n");
        sb.append("19. \"offerStrategy\": { \"primaryOffer\": string, \"cta\": string, \"urgencyTactic\": string }\n");
        sb.append("20. \"measurementPlan\": { \"kpiTargets\": { string: string }, \"evaluationWindow\": string, \"stopLossRules\": [string], \"scalingRules\": [string] }\n");
        sb.append("21. \"risksAndMitigations\": [ { \"risk\": string, \"mitigation\": string } ]\n");
        sb.append("22. \"first14DaysLearningPlan\": { \"dataToCollect\": [string], \"decisionsToDefer\": [string], \"testingPlan\": string, \"expectedOutcome\": string }\n");
        sb.append("23. \"humanReadablePlanMarkdown\": string — a full consultant-style explanation in Markdown that a business owner can read in a UI. ");
        sb.append("It should feel like a real human strategist wrote it. Use headings, bullet points, and clear language.\n\n");

        sb.append("Return ONLY the JSON object. No markdown fences, no explanation outside JSON.\n");

        return sb.toString();
    }

    private String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

