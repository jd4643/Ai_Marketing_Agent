package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.IntelPromptBuilder;
import com.marketing.strategy.service.intel.StrategyTemplateEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptBuilderInjectionTest {
  @Test
  void containsBusinessFactsAndMemoryAndIntelligenceSections() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    t.setDescription("Balanced online funnel");
    t.setChannelMixJson("{\"meta\":60}");
    t.setCampaignStructureJson("{\"top\":\"Prospecting\"}");
    t.setCreativeAnglesJson("[\"proof\"]");
    t.setKpiTargetsJson("{\"roas\":\"2+\"}");
    t.setRiskFactorsJson("[\"fatigue\"]");

    String prompt = new IntelPromptBuilder().build(
        Map.of("businessName", "Acme", "industry", "jewelry", "location", "Dubai", "priceRange", "premium", "targetAudience", "women", "websiteUrl", "https://acme.com"),
        "sales", 2000, t,
        List.of(Map.of("platform", "meta", "avgRoas", 2.1)),
        List.of(Map.of("hook", "offer")),
        List.of("wedding trends"),
        72,
        List.of(Map.of("node", "motion", "value", "ONLINE")),
        Map.of("matchedTemplateKey", "ONLINE_MID_TICKET_META_FUNNEL_RETARGET"),
        true);
    assertTrue(prompt.contains("Senior Marketing Consultant"));
    assertTrue(prompt.contains("CONTEXT A - Business Memory"));
    assertTrue(prompt.contains("CONTEXT B - Performance Memory"));
    assertTrue(prompt.contains("CONTEXT C - Creative Winners"));
    assertTrue(prompt.contains("CONTEXT D - Trend Signals"));
    assertTrue(prompt.contains("CONTEXT E - Strategy Intelligence"));
    assertTrue(prompt.contains("Acme"));
    assertTrue(prompt.contains("wedding trends"));
    assertTrue(prompt.contains("cold start".toLowerCase()));
  }
}
