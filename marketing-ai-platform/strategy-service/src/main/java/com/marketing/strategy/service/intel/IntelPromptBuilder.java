package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class IntelPromptBuilder {
    public String build(Map<String, Object> business, String objective, Object budget,
                        StrategyTemplateEntity template, String perfSummary,
                        List<String> trends, int confidenceScore, boolean coldStart) {
        StringBuilder sb = new StringBuilder();
        sb.append("Business=").append(business)
                .append("\nObjective=").append(objective)
                .append("\nBudget=").append(budget)
                .append("\nTemplateKey=").append(template.getTemplateKey())
                .append("\nTemplateChannelMix=").append(template.getChannelMixJson())
                .append("\nTemplateStructure=").append(template.getCampaignStructureJson())
                .append("\nPerformanceSummary=").append(perfSummary)
                .append("\nTrends=").append(trends)
                .append("\nConfidenceScore=").append(confidenceScore);
        if (coldStart) {
            sb.append("\nCOLD_START=true")
                    .append("\nIMPORTANT: This is a brand-new business with ZERO performance history and no creative winners.")
                    .append(" Prioritize data-collection campaigns, broader targeting, and conservative budget allocation.")
                    .append(" Recommend A/B testing frameworks and set realistic expectations for the ramp-up phase.");
        }
        sb.append("\n\nYou MUST return a JSON object with EXACTLY these keys:");
        sb.append("\n- platformBudgetSplit: object with keys meta, google, tiktok, youtube (numeric values)");
        sb.append("\n- campaignPlan: array of objects, each with keys: platform, dailyBudget, objective, targeting, creativeHook");
        sb.append("\n- funnelStrategy: string describing the funnel approach");
        sb.append("\n- expectedCPL: string estimate of cost per lead");
        sb.append("\n- expectedROAS: string estimate of return on ad spend");
        sb.append("\n- reasoning: string explaining the strategy");
        sb.append("\n- assumptions: array of strings listing key assumptions");
        sb.append("\n\nDo NOT use any other key names. Return only the JSON object, no markdown.");
        return sb.toString();
    }
}