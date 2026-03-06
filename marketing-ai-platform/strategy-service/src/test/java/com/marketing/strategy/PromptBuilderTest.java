package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.IntelPromptBuilder;
import com.marketing.strategy.service.intel.StrategyTemplateEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {
  @Test
  void includesConsultantRules() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_LOW_TICKET_UGC_BUNDLES");
    t.setDescription("UGC bundles");
    t.setChannelMixJson("{}");
    t.setCampaignStructureJson("{}");
    t.setCreativeAnglesJson("[]");
    t.setKpiTargetsJson("{}");
    t.setRiskFactorsJson("[]");
    String out = new IntelPromptBuilder().build(Map.of("industry", "jewelry", "businessName", "Acme"), "sales", 1000, t,
        List.of(Map.of("platform", "meta")), List.of(Map.of("hook", "offer")), List.of("trend"), 60,
        List.of(Map.of("node", "motion", "value", "ONLINE")), null, false);
    assertTrue(out.contains("non-technical owner"));
    assertTrue(out.contains("Do not invent performance history"));
  }
}
