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
  void containsAllFiveContextSections() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    t.setName("Mid-Ticket Meta Funnel");
    t.setDescription("Full-funnel retargeting strategy for mid-priced products");
    t.setChannelMixJson("{\"meta\":60}");
    t.setCampaignStructureJson("{\"top\":\"Prospecting\"}");
    t.setCreativeAnglesJson("[\"value\",\"trust\"]");
    t.setKpiTargetsJson("{\"ROAS\":\"3.0\"}");
    t.setRiskFactorsJson("[\"audience saturation\"]");

    String prompt = new IntelPromptBuilder().build(
        Map.of("businessName", "Acme Jewelry", "industry", "jewelry", "product", "rings",
            "targetAudience", "women 25-45"),
        "sales", 2000, t,
        "metrics=[{platform=meta}] winners=[{hook=offer}]", List.of("wedding trends"), 72, false);

    // A) Business Memory
    assertTrue(prompt.contains("BUSINESS MEMORY"));
    assertTrue(prompt.contains("Acme Jewelry"));
    assertTrue(prompt.contains("jewelry"));
    assertTrue(prompt.contains("rings"));

    // B) Performance Memory
    assertTrue(prompt.contains("PERFORMANCE MEMORY"));
    assertTrue(prompt.contains("metrics"));

    // C) Creative Winners
    assertTrue(prompt.contains("CREATIVE WINNERS"));
    assertTrue(prompt.contains("winners"));

    // D) Trend Signals
    assertTrue(prompt.contains("TREND SIGNALS"));
    assertTrue(prompt.contains("wedding trends"));

    // E) Strategy Intelligence
    assertTrue(prompt.contains("STRATEGY INTELLIGENCE"));
    assertTrue(prompt.contains("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"));
    assertTrue(prompt.contains("Mid-Ticket Meta Funnel"));
    assertTrue(prompt.contains("Full-funnel retargeting"));
    assertTrue(prompt.contains("72/100"));
  }
}
