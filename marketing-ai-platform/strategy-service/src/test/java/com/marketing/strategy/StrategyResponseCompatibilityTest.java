package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.model.StrategyResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StrategyResponseCompatibilityTest {
  @Test
  void keepsRequiredLegacyFields() {
    Set<String> names = Arrays.stream(StrategyResponse.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    assertNotNull(names);
    assertTrue(names.contains("platformBudgetSplit"));
    assertTrue(names.contains("campaignPlan"));
    assertTrue(names.contains("funnelStrategy"));
    assertTrue(names.contains("expectedCPL"));
    assertTrue(names.contains("expectedROAS"));
    assertTrue(names.contains("reasoning"));
    assertTrue(names.contains("assumptions"));
  }
}
