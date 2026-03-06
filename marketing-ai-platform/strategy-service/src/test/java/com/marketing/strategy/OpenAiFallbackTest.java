package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.marketing.strategy.service.intel.*;
import com.marketing.strategy.service.StrategyService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAiFallbackTest {
  @Test
  void normalizeFallsBackWhenSchemaIsIncomplete(){
    StrategyService s=new StrategyService(null,15,
        Mockito.mock(StrategyTemplateRepository.class), Mockito.mock(StrategyRunIntelRepository.class),
        new DecisionTreeSelector(), new ConfidenceScorer(), new PatternMatcher(), new IntelPromptBuilder(), new StrategyOutputValidator());
    Map<String, Object> incomplete = Map.of("requestId", UUID.randomUUID().toString());
    Map<String, BigDecimal> split = Map.of("meta", new BigDecimal("1000"), "google", BigDecimal.ZERO, "tiktok", BigDecimal.ZERO, "youtube", BigDecimal.ZERO);
    var out = s.normalizeOrFallback(incomplete, UUID.randomUUID(), split);
    assertNotNull(out.get("platformBudgetSplit"));
    assertNotNull(out.get("campaignPlan"));
  }

  @Test
  void fallbackContainsExecutionChecklistAndCreativeStrategy() throws Exception {
    StrategyService s=new StrategyService(null,15,
        Mockito.mock(StrategyTemplateRepository.class), Mockito.mock(StrategyRunIntelRepository.class),
        new DecisionTreeSelector(), new ConfidenceScorer(), new PatternMatcher(), new IntelPromptBuilder(), new StrategyOutputValidator());
    StrategyTemplateEntity template = new StrategyTemplateEntity();
    template.setTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    Method m = StrategyService.class.getDeclaredMethod("fallback", UUID.class, Map.class, StrategyTemplateEntity.class, int.class, List.class, Map.class, List.class, boolean.class, Map.class);
    m.setAccessible(true);
    Map<String, Object> out = (Map<String, Object>) m.invoke(s,
        UUID.randomUUID(),
        Map.of("meta", new BigDecimal("1000"), "google", BigDecimal.ZERO, "tiktok", BigDecimal.ZERO, "youtube", BigDecimal.ZERO),
        template,
        70,
        List.of(Map.of("node", "motion", "value", "ONLINE")),
        Map.of("businessName", "Acme", "industry", "jewelry", "websiteUrl", "https://acme.com"),
        List.of("trend"),
        true,
        null);
    assertNotNull(out.get("executionRoadmap"));
    assertNotNull(out.get("setupChecklist"));
    assertNotNull(out.get("creativeStrategy"));
    assertNotNull(out.get("first14DaysLearningPlan"));
  }
}
