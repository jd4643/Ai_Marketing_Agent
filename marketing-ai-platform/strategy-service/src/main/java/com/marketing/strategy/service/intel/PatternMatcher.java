package com.marketing.strategy.service.intel;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PatternMatcher {
  @Value("${STRATEGY_SIMILARITY_THRESHOLD:0.80}")
  double threshold=0.80;
  @Value("${STRATEGY_SUCCESS_MIN_ROAS:2.0}")
  double minRoas=2.0;
  @Value("${STRATEGY_SUCCESS_MIN_CONVERSIONS:10}")
  long minConversions=10;

  public record MatchResult(String templateKey, Map<String, Object> similarityMatch) {}

  public Optional<MatchResult> match(String currentTemplate, String objective, String industry, String motion, String budgetTier,
                                     BigDecimal avgRoas, long conversions,
                                     StrategyRunIntelEntity past, Map<String, Object> pastPerformance) {
    if (past == null) return Optional.empty();
    if (!objective.equalsIgnoreCase(past.getObjective())) return Optional.empty();

    double similarity = 0.7;
    if (currentTemplate.equals(past.getChosenTemplateKey())) similarity += 0.2;
    if (industry != null && !industry.isBlank()) similarity += 0.05;

    double pastRoas = asDouble(pastPerformance.get("roas"));
    long pastConv = asLong(pastPerformance.get("conversions"));
    boolean success = pastRoas >= minRoas || pastConv >= minConversions;
    if (similarity >= threshold && success) {
      return Optional.of(new MatchResult(
          past.getChosenTemplateKey(),
          Map.of(
              "matchedRequestId", past.getRequestId().toString(),
              "similarity", Math.min(similarity, 1.0),
              "matchedTemplateKey", past.getChosenTemplateKey(),
              "matchedRoas", pastRoas,
              "matchedConversions", pastConv)));
    }
    return Optional.empty();
  }

  private double asDouble(Object v) { return v == null ? 0.0 : Double.parseDouble(String.valueOf(v)); }
  private long asLong(Object v) { return v == null ? 0L : Long.parseLong(String.valueOf(v)); }
}
