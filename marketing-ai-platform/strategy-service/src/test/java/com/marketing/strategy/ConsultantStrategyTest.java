package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.*;

import com.marketing.strategy.service.StrategyService;
import com.marketing.strategy.service.intel.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for the consultant-style strategy upgrade:
 * - Anti-generic validation
 * - Rich fallback response
 * - Cold-start handling
 * - Backward compatibility of existing required fields
 */
class ConsultantStrategyTest {

    private StrategyService service;

    @BeforeEach
    void setUp() {
        service = new StrategyService(null, 15,
                Mockito.mock(StrategyTemplateRepository.class),
                Mockito.mock(StrategyRunIntelRepository.class),
                new DecisionTreeSelector(),
                new ConfidenceScorer(),
                new PatternMatcher(),
                new IntelPromptBuilder());
    }

    private StrategyTemplateEntity template(String key) {
        StrategyTemplateEntity t = new StrategyTemplateEntity();
        t.setId(UUID.randomUUID());
        t.setTemplateKey(key);
        t.setName("Test Template: " + key);
        t.setDescription("Test description for " + key);
        t.setChannelMixJson("{\"meta\":60,\"google\":30,\"tiktok\":10}");
        t.setCampaignStructureJson("{\"top\":\"Prospecting\",\"mid\":\"Retargeting\"}");
        t.setCreativeAnglesJson("[\"trust\",\"value\",\"social proof\"]");
        t.setKpiTargetsJson("{\"ROAS\":\"2.0\",\"CTR\":\"1.0%\"}");
        t.setRiskFactorsJson("[\"audience fatigue\",\"ad blindness\"]");
        return t;
    }

    private Map<String, Object> business(String name, String industry) {
        Map<String, Object> b = new HashMap<>();
        b.put("businessName", name);
        b.put("industry", industry);
        b.put("product", "rings");
        b.put("targetAudience", "women 25-45");
        b.put("priceRange", "$50-$200");
        b.put("websiteUrl", "https://example.com");
        return b;
    }

    private Map<String, BigDecimal> split() {
        return Map.of("meta", new BigDecimal("1000"), "google", new BigDecimal("600"),
                "tiktok", new BigDecimal("400"), "youtube", BigDecimal.ZERO);
    }

    // ─── Anti-Generic Validation Tests ─────────────────────────────────

    @Nested
    class AntiGenericValidation {

        @Test
        void rejectsResponseMissingBusinessName() {
            Map<String, Object> resp = new HashMap<>();
            resp.put("platformBudgetSplit", Map.of("meta", 1000));
            resp.put("campaignPlan", List.of());
            resp.put("reasoning", "Generic advice for any business");
            // No mention of "Acme Jewelry" or "jewelry" anywhere
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            List<String> violations = service.validateNotGeneric(resp, biz);
            assertFalse(violations.isEmpty(), "Should reject response missing business name");
            assertTrue(violations.stream().anyMatch(v -> v.contains("Acme Jewelry")));
        }

        @Test
        void rejectsResponseMissingIndustry() {
            Map<String, Object> resp = new HashMap<>();
            resp.put("platformBudgetSplit", Map.of("meta", 1000));
            resp.put("campaignPlan", List.of());
            resp.put("reasoning", "Strategy for Bright Star Corp");
            // Contains business name but not industry ("automotive")
            Map<String, Object> biz = business("Bright Star Corp", "automotive");
            biz.put("businessName", "Bright Star Corp");
            biz.put("industry", "automotive");
            List<String> violations = service.validateNotGeneric(resp, biz);
            assertTrue(violations.stream().anyMatch(v -> v.contains("automotive")));
        }

        @Test
        void rejectsFlatBudgetTemplateOnly() {
            // Response with ONLY old fields, zero consultant fields
            Map<String, Object> resp = new HashMap<>();
            resp.put("platformBudgetSplit", Map.of("meta", 1000));
            resp.put("campaignPlan", List.of(Map.of("platform", "meta")));
            resp.put("reasoning", "Strategy for Acme Jewelry in jewelry industry");
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            List<String> violations = service.validateNotGeneric(resp, biz);
            assertTrue(violations.stream().anyMatch(v -> v.contains("flat budget template")));
        }

        @Test
        void rejectsMissingExecutionRoadmap() {
            Map<String, Object> resp = buildAlmostComplete("Acme Jewelry", "jewelry");
            resp.remove("executionRoadmap");
            List<String> violations = service.validateNotGeneric(resp, business("Acme Jewelry", "jewelry"));
            assertTrue(violations.stream().anyMatch(v -> v.contains("executionRoadmap")));
        }

        @Test
        void rejectsMissingSetupChecklist() {
            Map<String, Object> resp = buildAlmostComplete("Acme Jewelry", "jewelry");
            resp.remove("setupChecklist");
            List<String> violations = service.validateNotGeneric(resp, business("Acme Jewelry", "jewelry"));
            assertTrue(violations.stream().anyMatch(v -> v.contains("setupChecklist")));
        }

        @Test
        void rejectsMissingCreativeStrategy() {
            Map<String, Object> resp = buildAlmostComplete("Acme Jewelry", "jewelry");
            resp.remove("creativeStrategy");
            List<String> violations = service.validateNotGeneric(resp, business("Acme Jewelry", "jewelry"));
            assertTrue(violations.stream().anyMatch(v -> v.contains("creativeStrategy")));
        }

        @Test
        void acceptsFullConsultantResponse() {
            Map<String, Object> resp = buildAlmostComplete("Acme Jewelry", "jewelry");
            List<String> violations = service.validateNotGeneric(resp, business("Acme Jewelry", "jewelry"));
            assertTrue(violations.isEmpty(), "Full consultant response should pass validation: " + violations);
        }

        @Test
        void rejectsNullResponse() {
            List<String> violations = service.validateNotGeneric(null, business("Acme", "tech"));
            assertFalse(violations.isEmpty());
            assertTrue(violations.get(0).contains("null or empty"));
        }

        private Map<String, Object> buildAlmostComplete(String name, String industry) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("platformBudgetSplit", Map.of("meta", 1000));
            resp.put("campaignPlan", List.of(Map.of("platform", "meta")));
            resp.put("reasoning", "Strategy for " + name + " in " + industry);
            resp.put("businessSnapshot", Map.of("summary", name + " in " + industry));
            resp.put("marketAnalysis", Map.of("competitionContext", industry + " analysis"));
            resp.put("customerPersona", Map.of("primaryBuyer", "Target buyer"));
            resp.put("whyThisStrategy", Map.of("whyChosenPlatforms", "Meta for " + industry));
            resp.put("platformStrategy", List.of(Map.of("platform", "meta")));
            resp.put("campaignArchitecture", List.of(Map.of("platform", "meta")));
            resp.put("creativeStrategy", Map.of("hooks", List.of("hook1")));
            resp.put("creativesNeeded", List.of(Map.of("type", "image")));
            resp.put("executionRoadmap", Map.of("week1", "Setup"));
            resp.put("setupChecklist", List.of(Map.of("item", "Pixel")));
            resp.put("measurementPlan", Map.of("kpiTargets", Map.of("CTR", "1%")));
            resp.put("risksAndMitigations", List.of(Map.of("risk", "low CTR")));
            resp.put("humanReadablePlanMarkdown", "# Plan for " + name);
            return resp;
        }
    }

    // ─── Rich Fallback Tests ───────────────────────────────────────────

    @Nested
    class RichFallback {

        @Test
        void fallbackContainsExecutionRoadmap() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("executionRoadmap"), "Fallback must contain executionRoadmap");
            @SuppressWarnings("unchecked")
            Map<String, Object> roadmap = (Map<String, Object>) fb.get("executionRoadmap");
            assertNotNull(roadmap.get("week1"));
            assertNotNull(roadmap.get("week2"));
            assertNotNull(roadmap.get("week3"));
            assertNotNull(roadmap.get("week4"));
        }

        @Test
        void fallbackContainsSetupChecklist() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("setupChecklist"), "Fallback must contain setupChecklist");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> checklist = (List<Map<String, Object>>) fb.get("setupChecklist");
            assertFalse(checklist.isEmpty());
        }

        @Test
        void fallbackContainsCreativeStrategy() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("creativeStrategy"), "Fallback must contain creativeStrategy");
        }

        @Test
        void fallbackContainsHumanReadableMarkdown() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("humanReadablePlanMarkdown"));
            String md = fb.get("humanReadablePlanMarkdown").toString();
            assertTrue(md.contains("Acme Jewelry"));
            assertTrue(md.contains("jewelry"));
        }

        @Test
        void fallbackContainsMeasurementPlan() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("measurementPlan"));
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) fb.get("measurementPlan");
            assertNotNull(mp.get("stopLossRules"));
            assertNotNull(mp.get("scalingRules"));
        }

        @Test
        void fallbackContainsRisksAndMitigations() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            assertNotNull(fb.get("risksAndMitigations"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> risks = (List<Map<String, Object>>) fb.get("risksAndMitigations");
            assertFalse(risks.isEmpty());
            assertNotNull(risks.get(0).get("risk"));
            assertNotNull(risks.get(0).get("mitigation"));
        }

        @Test
        void fallbackReferencesBusinessNameInSnapshot() {
            Map<String, Object> biz = business("Acme Jewelry", "jewelry");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_LOW_TICKET_UGC_BUNDLES"), split(), false);
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) fb.get("businessSnapshot");
            assertTrue(snapshot.get("summary").toString().contains("Acme Jewelry"));
        }

        @Test
        void enrichPreservesExistingLlmSections() {
            Map<String, Object> llmResp = new HashMap<>();
            llmResp.put("executionRoadmap", Map.of("week1", "LLM provided week 1"));
            llmResp.put("creativeStrategy", Map.of("hooks", List.of("LLM hook")));
            Map<String, Object> biz = business("Acme", "jewelry");
            Map<String, Object> enriched = service.enrichWithFallbackSections(
                    llmResp, biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), false);
            // LLM-provided sections should NOT be overwritten
            @SuppressWarnings("unchecked")
            Map<String, Object> roadmap = (Map<String, Object>) enriched.get("executionRoadmap");
            assertEquals("LLM provided week 1", roadmap.get("week1"));
            // Missing sections should be filled from fallback
            assertNotNull(enriched.get("setupChecklist"));
            assertNotNull(enriched.get("measurementPlan"));
        }
    }

    // ─── Cold-Start Tests ──────────────────────────────────────────────

    @Nested
    class ColdStart {

        @Test
        void coldStartFallbackContainsFirst14DaysLearningPlan() {
            Map<String, Object> biz = business("NewBiz", "saas");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), true);
            assertNotNull(fb.get("first14DaysLearningPlan"), "Cold start must include first14DaysLearningPlan");
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) fb.get("first14DaysLearningPlan");
            assertNotNull(plan.get("dataToCollect"));
            assertNotNull(plan.get("decisionsToDefer"));
            assertNotNull(plan.get("testingPlan"));
        }

        @Test
        void coldStartMarkdownMentionsLearningPhase() {
            Map<String, Object> biz = business("NewBiz", "saas");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET"), split(), true);
            String md = fb.get("humanReadablePlanMarkdown").toString();
            assertTrue(md.contains("Learning Phase") || md.contains("learning"),
                    "Cold start markdown should mention learning phase");
            assertTrue(md.contains("NewBiz"));
        }

        @Test
        void coldStartPromptIncludesConservativeInstructions() {
            StrategyTemplateEntity t = template("ONLINE_MID_TICKET_META_FUNNEL_RETARGET");
            String prompt = new IntelPromptBuilder().build(
                    Map.of("businessName", "FreshStart", "industry", "ecommerce"),
                    "sales", 1500, t, "metrics=[] winners=[]", List.of(), 35, true);
            assertTrue(prompt.contains("conservative"));
            assertTrue(prompt.contains("first14DaysLearningPlan"));
            assertTrue(prompt.contains("COLD-START"));
        }
    }

    // ─── Backward Compatibility Tests ──────────────────────────────────

    @Nested
    class BackwardCompatibility {

        @Test
        void existingRequiredFieldsPresentInNormalizedResponse() {
            Map<String, Object> llmResp = new HashMap<>();
            llmResp.put("platformBudgetSplit", Map.of("meta", 500, "google", 300, "tiktok", 200, "youtube", 0));
            llmResp.put("campaignPlan", List.of(Map.of("platform", "meta", "dailyBudget", 16.67)));
            llmResp.put("funnelStrategy", "Full funnel");
            llmResp.put("expectedCPL", "$5-$10");
            llmResp.put("expectedROAS", "2.0-3.0x");
            llmResp.put("reasoning", "Test reasoning for Acme Jewelry in jewelry");
            llmResp.put("assumptions", List.of("Assumption 1"));

            UUID requestId = UUID.randomUUID();
            Map<String, Object> result = service.normalizeOrFallback(llmResp, requestId, split());

            assertEquals(requestId.toString(), result.get("requestId"));
            assertNotNull(result.get("strategyVersion"));
            assertNotNull(result.get("platformBudgetSplit"));
            assertNotNull(result.get("campaignPlan"));
            assertNotNull(result.get("funnelStrategy"));
            assertNotNull(result.get("expectedCPL"));
            assertNotNull(result.get("expectedROAS"));
            assertNotNull(result.get("reasoning"));
            assertNotNull(result.get("assumptions"));
        }

        @Test
        void legacyNormalizeFallsBackWhenSchemaIsIncomplete() {
            Map<String, Object> incomplete = Map.of("requestId", UUID.randomUUID().toString());
            Map<String, Object> out = service.normalizeOrFallback(incomplete, UUID.randomUUID(), split());
            assertNotNull(out.get("platformBudgetSplit"));
            assertNotNull(out.get("campaignPlan"));
            assertNotNull(out.get("funnelStrategy"));
        }

        @Test
        void enrichedFallbackAlsoHasRequiredFields() {
            Map<String, Object> biz = business("CompatTest", "retail");
            Map<String, Object> fb = service.enrichWithFallbackSections(
                    new HashMap<>(), biz, template("OFFLINE_MID_TICKET_FESTIVAL_FOOTFALL"), split(), false);
            // Fallback enrichment fills all consultant fields; check required ones survive
            assertNotNull(fb.get("businessSnapshot"));
            assertNotNull(fb.get("executionRoadmap"));
            assertNotNull(fb.get("setupChecklist"));
            assertNotNull(fb.get("creativeStrategy"));
            assertNotNull(fb.get("measurementPlan"));
            assertNotNull(fb.get("risksAndMitigations"));
            assertNotNull(fb.get("first14DaysLearningPlan"));
            assertNotNull(fb.get("humanReadablePlanMarkdown"));
        }
    }
}
