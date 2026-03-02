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
  void containsTemplateAndMetricsWinnersAndTrends() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    t.setChannelMixJson("{\"meta\":60}");
    t.setCampaignStructureJson("{\"top\":\"Prospecting\"}");

    String prompt = new IntelPromptBuilder().build(
        Map.of("industry", "jewelry"), "sales", 2000, t,
        "metrics=[{platform=meta}] winners=[{hook=offer}]", List.of("wedding trends"), 72);
    assertTrue(prompt.contains("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"));
    assertTrue(prompt.contains("metrics"));
    assertTrue(prompt.contains("winners"));
    assertTrue(prompt.contains("wedding trends"));
  }
}
