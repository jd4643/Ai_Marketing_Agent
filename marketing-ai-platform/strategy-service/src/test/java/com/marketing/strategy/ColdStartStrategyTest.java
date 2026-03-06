package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marketing.strategy.service.intel.IntelPromptBuilder;
import com.marketing.strategy.service.intel.StrategyTemplateEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColdStartStrategyTest {
  @Test
  void coldStartPromptContainsLearningPlanInstruction() {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey("ONLINE_LOW_TICKET_UGC_BUNDLES");
    t.setDescription("desc");
    t.setChannelMixJson("{}");
    t.setCampaignStructureJson("{}");
    t.setCreativeAnglesJson("[]");
    t.setKpiTargetsJson("{}");
    t.setRiskFactorsJson("[]");
    String prompt = new IntelPromptBuilder().build(
        Map.of("businessName", "Acme", "industry", "jewelry"),
        "sales", 500, t, List.of(), List.of(), List.of(), 55, List.of(), null, true);
    assertTrue(prompt.toLowerCase().contains("first 14-day learning"));
  }
}
