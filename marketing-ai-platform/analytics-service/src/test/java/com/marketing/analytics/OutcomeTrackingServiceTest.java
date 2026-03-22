package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.CreativeAssetPerformance;
import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.model.RecommendationOutcome;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.repo.RecommendationOutcomeRepository;
import com.marketing.analytics.service.LearningEventService;
import com.marketing.analytics.service.OutcomeTrackingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutcomeTrackingServiceTest {

    @Mock RecommendationOutcomeRepository outcomeRepo;
    @Mock CreativeOptimizationRecommendationRepository recRepo;
    @Mock CampaignMetricRepository metricRepo;
    @Mock CreativeAssetPerformanceRepository perfRepo;
    @Mock LearningEventService learningEventService;
    @InjectMocks OutcomeTrackingService service;

    static final UUID BIZ = UUID.randomUUID();
    static final UUID REC_ID = UUID.randomUUID();
    static final UUID ASSET_ID = UUID.randomUUID();

    private CreativeOptimizationRecommendation buildRec(String type) {
        CreativeOptimizationRecommendation rec = new CreativeOptimizationRecommendation();
        rec.setId(REC_ID);
        rec.setBusinessId(BIZ);
        rec.setCreativeAssetId(ASSET_ID);
        rec.setRecommendationType(type);
        rec.setPriority("HIGH");
        rec.setTitle("Test " + type);
        rec.setDescription("Desc");
        rec.setReasoningJson("{}");
        rec.setSuggestedNextAction("Do something");
        rec.setStatus("APPLIED");
        rec.setCreatedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        return rec;
    }

    private RecommendationOutcome buildOutcome(String verdict, Instant actionDate) {
        RecommendationOutcome o = new RecommendationOutcome();
        o.setId(UUID.randomUUID());
        o.setRecommendationId(REC_ID);
        o.setBusinessId(BIZ);
        o.setActionTaken("APPLIED");
        o.setActionDate(actionDate);
        o.setBaselineSnapshot("{\"totalSpend\":100,\"overallRoas\":1.5}");
        o.setEvaluationWindowDays(7);
        o.setOutcomeVerdict(verdict);
        o.setCreatedAt(actionDate);
        o.setUpdatedAt(actionDate);
        return o;
    }

    @Nested class RecordBaselineTests {

        @Test void recordBaselineNewRecommendation() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE");
            when(outcomeRepo.findByRecommendationId(REC_ID)).thenReturn(Optional.empty());
            when(recRepo.findById(REC_ID)).thenReturn(Optional.of(rec));
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any())).thenReturn(
                    Collections.singletonList(new Object[]{ BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), 50000L, 2000L, 100L })
            );
            when(perfRepo.findById(ASSET_ID)).thenReturn(Optional.of(buildAssetPerf()));
            when(outcomeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Map<String, Object> result = service.recordBaseline(REC_ID, "APPLIED");

            assertNotNull(result.get("id"));
            assertEquals(REC_ID, result.get("recommendationId"));
            assertEquals("PENDING", result.get("outcomeVerdict"));
            assertEquals("APPLIED", result.get("actionTaken"));
            verify(outcomeRepo).save(any(RecommendationOutcome.class));
            verify(learningEventService).record(eq(BIZ), eq("BASELINE_CAPTURED"), eq("RECOMMENDATION"),
                    eq(REC_ID), anyMap(), eq("INFO"));
        }

        @Test void recordBaselineIdempotent() {
            RecommendationOutcome existing = buildOutcome("PENDING", Instant.now());
            when(outcomeRepo.findByRecommendationId(REC_ID)).thenReturn(Optional.of(existing));

            Map<String, Object> result = service.recordBaseline(REC_ID, "APPLIED");

            assertNotNull(result);
            verify(outcomeRepo, never()).save(any());
            verify(learningEventService, never()).record(any(), any(), any(), any(), anyMap(), any());
        }

        @Test void recordBaselineRecNotFound() {
            when(outcomeRepo.findByRecommendationId(REC_ID)).thenReturn(Optional.empty());
            when(recRepo.findById(REC_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> service.recordBaseline(REC_ID, "APPLIED"));
        }
    }

    @Nested class EvaluateAllPendingTests {

        @Test void evaluatesPendingOutcomesWithElapsedWindow() {
            Instant pastAction = Instant.now().minus(10, ChronoUnit.DAYS);
            RecommendationOutcome pending = buildOutcome("PENDING", pastAction);
            CreativeOptimizationRecommendation rec = buildRec("SCALE");

            when(outcomeRepo.findByOutcomeVerdictAndActionDateBefore(eq("PENDING"), any()))
                    .thenReturn(List.of(pending));
            when(recRepo.findById(pending.getRecommendationId())).thenReturn(Optional.of(rec));
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any())).thenReturn(
                    Collections.singletonList(new Object[]{ BigDecimal.valueOf(1200), BigDecimal.valueOf(3000), 60000L, 3000L, 150L })
            );
            when(perfRepo.findById(ASSET_ID)).thenReturn(Optional.of(buildAssetPerf()));
            when(outcomeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Map<String, Object> result = service.evaluateAllPending();

            assertEquals(1, result.get("evaluated"));
            assertNotEquals("PENDING", pending.getOutcomeVerdict());
            verify(outcomeRepo).save(pending);
        }

        @Test void skipsOutcomesInWindow() {
            Instant recentAction = Instant.now().minus(3, ChronoUnit.DAYS);
            RecommendationOutcome pending = buildOutcome("PENDING", recentAction);

            when(outcomeRepo.findByOutcomeVerdictAndActionDateBefore(eq("PENDING"), any()))
                    .thenReturn(List.of(pending));

            Map<String, Object> result = service.evaluateAllPending();

            assertEquals(0, result.get("evaluated"));
            verify(outcomeRepo, never()).save(any());
        }

        @Test void emptyPendingList() {
            when(outcomeRepo.findByOutcomeVerdictAndActionDateBefore(eq("PENDING"), any()))
                    .thenReturn(List.of());

            Map<String, Object> result = service.evaluateAllPending();

            assertEquals(0, result.get("evaluated"));
        }
    }

    @Nested class ImpactScoreTests {

        @Test void scalePositiveImpact() {
            Map<String, Object> deltas = buildDeltas(10, 15, 5, 12, 20);
            double score = service.computeImpactScore(deltas, "SCALE");
            assertTrue(score > 0, "SCALE with positive deltas should have positive score");
            assertTrue(score <= 1.0);
        }

        @Test void stopReducesSpend() {
            Map<String, Object> deltas = buildDeltas(20, 5, -30, 10, -10);
            double score = service.computeImpactScore(deltas, "STOP");
            // STOP: +ROAS*0.4 + conv*0.1 - (-spend)*0.3 + assetRoas*0.2
            // = 20*0.4 + 5*0.1 - (-30)*0.3 + 10*0.2 = 8+0.5+9+2 = 19.5 / 100 = 0.195
            assertTrue(score > 0, "STOP with reduced spend and improved ROAS should be positive");
        }

        @Test void negativeDeltas() {
            Map<String, Object> deltas = buildDeltas(-20, -30, 10, -25, -15);
            double score = service.computeImpactScore(deltas, "SCALE");
            assertTrue(score < 0, "Negative deltas should give negative score");
            assertTrue(score >= -1.0);
        }

        @Test void zeroDeltas() {
            Map<String, Object> deltas = buildDeltas(0, 0, 0, 0, 0);
            double score = service.computeImpactScore(deltas, "SCALE");
            assertEquals(0, score, 0.001);
        }

        @Test void clampedToRange() {
            Map<String, Object> deltas = buildDeltas(500, 500, 500, 500, 500);
            double score = service.computeImpactScore(deltas, "SCALE");
            assertTrue(score <= 1.0, "Score should be clamped to 1.0");
        }
    }

    @Nested class DeriveVerdictTests {

        @Test void positive() { assertEquals("POSITIVE", service.deriveVerdict(0.10)); }
        @Test void borderlinePositive() { assertEquals("POSITIVE", service.deriveVerdict(0.05)); }
        @Test void negative() { assertEquals("NEGATIVE", service.deriveVerdict(-0.10)); }
        @Test void borderlineNegative() { assertEquals("NEGATIVE", service.deriveVerdict(-0.05)); }
        @Test void neutralAboveThreshold() { assertEquals("NEUTRAL", service.deriveVerdict(0.04)); }
        @Test void neutralBelowThreshold() { assertEquals("NEUTRAL", service.deriveVerdict(-0.04)); }
        @Test void neutralZero() { assertEquals("NEUTRAL", service.deriveVerdict(0.0)); }
    }

    @Nested class GetOutcomesTests {

        @Test void getWithVerdict() {
            RecommendationOutcome o = buildOutcome("POSITIVE", Instant.now());
            o.setImpactScore(0.15);
            when(outcomeRepo.findByBusinessIdAndOutcomeVerdictOrderByCreatedAtDesc(BIZ, "POSITIVE"))
                    .thenReturn(List.of(o));

            List<Map<String, Object>> results = service.getOutcomes(BIZ, "POSITIVE");

            assertEquals(1, results.size());
            assertEquals("POSITIVE", results.get(0).get("outcomeVerdict"));
        }

        @Test void getAllOutcomes() {
            when(outcomeRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

            List<Map<String, Object>> results = service.getOutcomes(BIZ, null);

            assertEquals(0, results.size());
            verify(outcomeRepo).findByBusinessIdOrderByCreatedAtDesc(BIZ);
        }
    }

    @Nested class OutcomeStatsTests {

        @Test void statsWithMixedOutcomes() {
            List<RecommendationOutcome> outcomes = List.of(
                    buildOutcomeWithVerdict("POSITIVE", 0.3),
                    buildOutcomeWithVerdict("POSITIVE", 0.2),
                    buildOutcomeWithVerdict("NEGATIVE", -0.15),
                    buildOutcomeWithVerdict("PENDING", null)
            );
            when(outcomeRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(outcomes);

            Map<String, Object> stats = service.getOutcomeStats(BIZ);

            assertEquals(BIZ, stats.get("businessId"));
            assertEquals(4, stats.get("totalOutcomes"));
            assertEquals(2L, stats.get("positive"));
            assertEquals(1L, stats.get("negative"));
            assertEquals(1L, stats.get("pending"));
        }

        @Test void statsEmpty() {
            when(outcomeRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

            Map<String, Object> stats = service.getOutcomeStats(BIZ);

            assertEquals(0, stats.get("totalOutcomes"));
            assertEquals(0.0, (double) stats.get("successRate"), 0.01);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CreativeAssetPerformance buildAssetPerf() {
        CreativeAssetPerformance perf = new CreativeAssetPerformance();
        perf.setId(ASSET_ID);
        perf.setBusinessId(BIZ);
        perf.setPlatform("META");
        perf.setClassification("WINNER");
        perf.setPerformanceScore(BigDecimal.valueOf(85));
        perf.setConfidenceScore(BigDecimal.valueOf(90));
        perf.setImpressions(50000L);
        perf.setClicks(2000L);
        perf.setConversions(100L);
        perf.setSpend(BigDecimal.valueOf(500));
        perf.setRoas(BigDecimal.valueOf(3.5));
        perf.setCreatedAt(Instant.now());
        perf.setUpdatedAt(Instant.now());
        return perf;
    }

    private Map<String, Object> buildDeltas(double roas, double conv, double spend, double assetRoas, double impressions) {
        Map<String, Object> deltas = new LinkedHashMap<>();
        deltas.put("overallRoas", Map.of("percentChange", BigDecimal.valueOf(roas)));
        deltas.put("totalConversions", Map.of("percentChange", BigDecimal.valueOf(conv)));
        deltas.put("totalSpend", Map.of("percentChange", BigDecimal.valueOf(spend)));
        deltas.put("assetRoas", Map.of("percentChange", BigDecimal.valueOf(assetRoas)));
        deltas.put("totalImpressions", Map.of("percentChange", BigDecimal.valueOf(impressions)));
        return deltas;
    }

    private RecommendationOutcome buildOutcomeWithVerdict(String verdict, Double impactScore) {
        RecommendationOutcome o = new RecommendationOutcome();
        o.setId(UUID.randomUUID());
        o.setRecommendationId(UUID.randomUUID());
        o.setBusinessId(BIZ);
        o.setActionTaken("APPLIED");
        o.setActionDate(Instant.now());
        o.setEvaluationWindowDays(7);
        o.setOutcomeVerdict(verdict);
        o.setImpactScore(impactScore);
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());
        return o;
    }
}
