package com.marketing.strategy.service.intel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StrategyOutputValidator {
  public boolean isGeneric(Map<String, Object> response, Map<String, Object> business) {
    if (response == null || response.isEmpty()) return true;
    String text = flatten(response).toLowerCase(Locale.ROOT);
    String businessName = String.valueOf(business.getOrDefault("businessName", "")).toLowerCase(Locale.ROOT);
    String industry = String.valueOf(business.getOrDefault("industry", "")).toLowerCase(Locale.ROOT);

    boolean hasBusinessRef = (!businessName.isBlank() && text.contains(businessName)) || (!industry.isBlank() && text.contains(industry));
    boolean hasPlatformWhy = response.get("whyThisStrategy") != null;
    boolean hasRoadmap = response.get("executionRoadmap") instanceof List<?> roadmap && !roadmap.isEmpty();
    boolean hasChecklist = response.get("setupChecklist") instanceof List<?> checklist && !checklist.isEmpty();
    boolean hasCreative = response.get("creativeStrategy") != null || response.get("creativesNeeded") != null;
    boolean flatOnly = response.get("platformBudgetSplit") != null && response.get("campaignPlan") != null
        && response.get("executionRoadmap") == null && response.get("setupChecklist") == null;

    return !hasBusinessRef || !hasPlatformWhy || !hasRoadmap || !hasChecklist || !hasCreative || flatOnly;
  }

  private String flatten(Map<String, Object> response) {
    StringBuilder sb = new StringBuilder();
    response.forEach((k, v) -> sb.append(k).append(':').append(v).append('\n'));
    return sb.toString();
  }
}
