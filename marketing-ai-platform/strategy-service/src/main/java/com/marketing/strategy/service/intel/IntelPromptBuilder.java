package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntelPromptBuilder {
  public String build(
      Map<String, Object> business,
      String objective,
      Object budget,
      StrategyTemplateEntity template,
      List<Map<String, Object>> metrics,
      List<Map<String, Object>> winners,
      List<String> trends,
      int confidenceScore,
      List<Map<String, Object>> decisionPath,
      Map<String, Object> similarityMatch,
      boolean coldStart
  ) {
    return "ROLE: Senior Marketing Consultant and Growth Strategist\n"
        + "MANDATE: Return non-generic, business-specific marketing strategy for a non-technical owner.\n"
        + "STRICT REQUIREMENTS:\n"
        + "- No reusable/generic template language.\n"
        + "- Explicitly reference business facts: business name, industry, location, price range, target audience, website/offline context.\n"
        + "- Explain WHY each platform is selected and WHY others are lower priority.\n"
        + "- Provide HOW to execute campaigns, WHAT creatives are needed, and week-by-week roadmap.\n"
        + "- Include tracking setup, landing page guidance, retargeting logic, KPIs, stop-loss/scaling rules, and risks/mitigations.\n"
        + "- Do not invent performance history or fake metrics.\n"
        + "- Avoid vague terms like 'optimize regularly' without concrete actions.\n"
        + (coldStart ? "- Cold start: no reliable ad history, use conservative ranges and emphasize first 14-day learning/testing plan.\n" : "")
        + "- Keep deterministic strategy selection as source of truth; do not override chosen template intent.\n"
        + "OUTPUT: Strict JSON only matching schema with existing fields plus rich optional consultant sections.\n\n"
        + "CONTEXT A - Business Memory:\n" + business + "\n\n"
        + "CONTEXT B - Performance Memory (30d aggregated):\n" + metrics + "\n\n"
        + "CONTEXT C - Creative Winners:\n" + winners + "\n\n"
        + "CONTEXT D - Trend Signals (last 7d):\n" + trends + "\n\n"
        + "CONTEXT E - Strategy Intelligence (deterministic source of truth):\n"
        + "objective=" + objective + "\n"
        + "budget=" + budget + "\n"
        + "chosenTemplateKey=" + template.getTemplateKey() + "\n"
        + "templateDescription=" + template.getDescription() + "\n"
        + "templateChannelMix=" + template.getChannelMixJson() + "\n"
        + "templateCampaignStructure=" + template.getCampaignStructureJson() + "\n"
        + "templateCreativeAngles=" + template.getCreativeAnglesJson() + "\n"
        + "templateKpiTargets=" + template.getKpiTargetsJson() + "\n"
        + "templateRiskFactors=" + template.getRiskFactorsJson() + "\n"
        + "confidenceScore=" + confidenceScore + "\n"
        + "decisionPath=" + decisionPath + "\n"
        + "similarityMatch=" + (similarityMatch == null ? "none" : similarityMatch) + "\n";
  }
}
