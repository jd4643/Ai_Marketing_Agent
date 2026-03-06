package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.IntelPromptBuilder;
import com.marketing.strategy.service.intel.StrategyTemplateEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {
  @Test
  void includesInjectedSummary() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_LOW_TICKET_UGC_BUNDLES");
    t.setChannelMixJson("{}");
    t.setCampaignStructureJson("{}");
    String out = new IntelPromptBuilder().build(Map.of("industry", "jewelry"), "sales", 1000, t,
        "metrics=[{platform=meta}] winners=[{hook=offer}]", List.of("trend"), 60, false);
    assertTrue(out.contains("metrics"));
    assertTrue(out.contains("offer"));
    assertFalse(out.contains("COLD_START"));
  }

  @Test
  void coldStartInjectsColdStartContext() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
    t.setChannelMixJson("{}");
    t.setCampaignStructureJson("{}");
    String out = new IntelPromptBuilder().build(Map.of("industry", "saas"), "leads", 5000, t,
        "metrics=[] winners=[]", List.of("ai"), 40, true);
    assertTrue(out.contains("COLD_START=true"));
    assertTrue(out.contains("ZERO performance history"));
    assertTrue(out.contains("A/B testing"));
  }
}
