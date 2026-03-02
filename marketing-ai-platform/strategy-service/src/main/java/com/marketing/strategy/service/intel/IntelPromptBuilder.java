package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntelPromptBuilder {
  public String build(Map<String, Object> business, String objective, Object budget,
                      StrategyTemplateEntity template, String perfSummary,
                      List<String> trends, int confidenceScore) {
    return "Business=" + business +
        "\nObjective=" + objective +
        "\nBudget=" + budget +
        "\nTemplateKey=" + template.getTemplateKey() +
        "\nTemplateChannelMix=" + template.getChannelMixJson() +
        "\nTemplateStructure=" + template.getCampaignStructureJson() +
        "\nPerformanceSummary=" + perfSummary +
        "\nTrends=" + trends +
        "\nConfidenceScore=" + confidenceScore +
        "\nReturn strict JSON matching schema only.";
  }
}
