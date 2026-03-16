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

  private StrategyTemplateEntity template(String key) {
    StrategyTemplateEntity t = new StrategyTemplateEntity();
    t.setId(UUID.randomUUID());
    t.setTemplateKey(key);
    t.setName("Test Template");
    t.setDescription("Template description for testing");
    t.setChannelMixJson("{\"meta\":60,\"google\":30}");
    t.setCampaignStructureJson("{\"top\":\"Prospecting\"}");
    t.setCreativeAnglesJson("[\"trust\",\"value\"]");
    t.setKpiTargetsJson("{\"ROAS\":\"2.0\"}");
    t.setRiskFactorsJson("[\"audience fatigue\"]");
    return t;
  }

  @Test
  void includesBusinessFactsAndConsultantRole() {
    String out = new IntelPromptBuilder().build(
        Map.of("businessName", "Acme Jewelry", "industry", "jewelry", "product", "rings",
            "targetAudience", "women 25-45", "priceRange", "$50-$200", "websiteUrl", "https://acme.com"),
        "sales", 2000, template("ONLINE_LOW_TICKET_UGC_BUNDLES"),
        "metrics=[{platform=meta}] winners=[{hook=offer}]", List.of("trend"), 60, false);
    // Consultant role
    assertTrue(out.contains("Senior Marketing Consultant"));
    // Business facts injected individually
    assertTrue(out.contains("Acme Jewelry"));
    assertTrue(out.contains("jewelry"));
    assertTrue(out.contains("rings"));
    assertTrue(out.contains("women 25-45"));
    assertTrue(out.contains("$50-$200"));
    assertTrue(out.contains("https://acme.com"));
    // Template key and details
    assertTrue(out.contains("ONLINE_LOW_TICKET_UGC_BUNDLES"));
    assertTrue(out.contains("Test Template"));
    // Performance and winners injected
    assertTrue(out.contains("metrics"));
    assertTrue(out.contains("offer"));
    // Anti-generic rules
    assertTrue(out.contains("Do NOT give generic"));
    assertTrue(out.contains("Do NOT invent performance history"));
    assertTrue(out.contains("Do NOT override the chosen strategy template"));
    // Schema keys
    assertTrue(out.contains("businessSnapshot"));
    assertTrue(out.contains("executionRoadmap"));
    assertTrue(out.contains("setupChecklist"));
    assertTrue(out.contains("creativeStrategy"));
    assertTrue(out.contains("humanReadablePlanMarkdown"));
    // No cold start
    assertFalse(out.contains("COLD-START INSTRUCTIONS"));
  }

  @Test
  void coldStartInjectsLearningPlanAndConservativeLanguage() {
    String out = new IntelPromptBuilder().build(
        Map.of("businessName", "NewCo SaaS", "industry", "saas"),
        "leads", 5000, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"),
        "metrics=[] winners=[]", List.of("ai"), 40, true);
    // Cold-start section present
    assertTrue(out.contains("COLD-START INSTRUCTIONS"));
    assertTrue(out.contains("ZERO ad history"));
    assertTrue(out.contains("first 14 days"));
    assertTrue(out.contains("A/B testing"));
    assertTrue(out.contains("conservative"));
    assertTrue(out.contains("first14DaysLearningPlan"));
    // Performance section says NO DATA
    assertTrue(out.contains("NO HISTORICAL PERFORMANCE DATA"));
    // Creative winners section says no winners
    assertTrue(out.contains("No creative winners yet"));
  }

  @Test
  void trendsAreListedIndividually() {
    String out = new IntelPromptBuilder().build(
        Map.of("businessName", "TrendBiz", "industry", "fashion"),
        "sales", 1000, template("ONLINE_LOW_TICKET_UGC_BUNDLES"),
        "metrics=[] winners=[]", List.of("summer dresses", "sustainable fashion", "boho chic"), 55, false);
    assertTrue(out.contains("- summer dresses"));
    assertTrue(out.contains("- sustainable fashion"));
    assertTrue(out.contains("- boho chic"));
  }

  @Test
  void offlineBusinessShowsNoneForWebsite() {
    String out = new IntelPromptBuilder().build(
        Map.of("businessName", "Local Shop", "industry", "retail"),
        "traffic", 500, template("OFFLINE_MID_TICKET_FESTIVAL_FOOTFALL"),
        "metrics=[] winners=[]", List.of(), 50, false);
    assertTrue(out.contains("NONE (offline business)"));
  }

  @Test
  void templateIntelligenceFieldsInjected() {
    String out = new IntelPromptBuilder().build(
        Map.of("businessName", "IntelTest", "industry", "tech"),
        "leads", 3000, template("ONLINE_HIGH_TICKET_LEADGEN_APPOINTMENT"),
        "metrics=[{platform=google}] winners=[{hook=demo}]", List.of("ai agents"), 82, false);
    assertTrue(out.contains("channelMix:"));
    assertTrue(out.contains("campaignStructure:"));
    assertTrue(out.contains("creativeAngles:"));
    assertTrue(out.contains("kpiTargets:"));
    assertTrue(out.contains("riskFactors:"));
    assertTrue(out.contains("confidenceScore: 82/100"));
    assertTrue(out.contains("deterministic source of truth"));
  }
}
