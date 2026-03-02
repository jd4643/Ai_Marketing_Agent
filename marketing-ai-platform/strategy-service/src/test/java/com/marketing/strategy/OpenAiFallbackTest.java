package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.marketing.strategy.service.intel.IntelPromptBuilder;
import com.marketing.strategy.service.intel.PatternMatcher;
import com.marketing.strategy.service.intel.StrategyRunIntelRepository;
import com.marketing.strategy.service.intel.StrategyTemplateRepository;
import com.marketing.strategy.service.intel.ConfidenceScorer;
import com.marketing.strategy.service.intel.DecisionTreeSelector;
import com.marketing.strategy.service.StrategyService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAiFallbackTest {
  @Test
  void normalizeFallsBackWhenSchemaIsIncomplete(){
    StrategyService s=new StrategyService(null,15,
        Mockito.mock(StrategyTemplateRepository.class), Mockito.mock(StrategyRunIntelRepository.class),
        new DecisionTreeSelector(), new ConfidenceScorer(), new PatternMatcher(), new IntelPromptBuilder());
    Map<String, Object> incomplete = Map.of("requestId", UUID.randomUUID().toString());
    Map<String, BigDecimal> split = Map.of("meta", new BigDecimal("1000"), "google", BigDecimal.ZERO, "tiktok", BigDecimal.ZERO, "youtube", BigDecimal.ZERO);
    var out = s.normalizeOrFallback(incomplete, UUID.randomUUID(), split);
    assertNotNull(out.get("platformBudgetSplit"));
    assertNotNull(out.get("campaignPlan"));
  }
}
