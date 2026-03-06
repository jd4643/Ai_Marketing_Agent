package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.StrategyOutputValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyOutputValidatorTest {
  @Test
  void rejectsGenericFlatBudgetOutput() {
    StrategyOutputValidator v = new StrategyOutputValidator();
    boolean generic = v.isGeneric(
        Map.of("platformBudgetSplit", Map.of("meta", 1000), "campaignPlan", List.of()),
        Map.of("businessName", "Acme", "industry", "jewelry"));
    assertTrue(generic);
  }

  @Test
  void acceptsDetailedBusinessSpecificOutput() {
    StrategyOutputValidator v = new StrategyOutputValidator();
    boolean generic = v.isGeneric(
        Map.of(
            "reasoning", "Acme jewelry relies on trust and high intent discovery",
            "whyThisStrategy", Map.of("why", "Meta + Google fit jewelry demand"),
            "executionRoadmap", List.of(Map.of("week", "Week 1")),
            "setupChecklist", List.of("tracking"),
            "creativeStrategy", Map.of("angles", List.of("proof"))),
        Map.of("businessName", "Acme", "industry", "jewelry"));
    assertFalse(generic);
  }
}
