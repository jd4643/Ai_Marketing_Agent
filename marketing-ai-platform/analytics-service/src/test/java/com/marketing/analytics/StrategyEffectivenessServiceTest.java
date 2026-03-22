package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.model.StrategyEffectiveness;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.repo.StrategyEffectivenessRepository;
import com.marketing.analytics.service.LearningEventService;
import com.marketing.analytics.service.StrategyEffectivenessService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.sql.DataSource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StrategyEffectivenessServiceTest {

    @Mock DataSource ds;
    @Mock StrategyEffectivenessRepository effectivenessRepo;
    @Mock CampaignMetricRepository metricRepo;
    @Mock CreativeAssetPerformanceRepository perfRepo;
    @Mock CreativeOptimizationRecommendationRepository recRepo;
    @Mock LearningEventService learningEventService;
    @InjectMocks StrategyEffectivenessService service;

    static final UUID BIZ = UUID.randomUUID();

    @Nested class FreshnessScoreTests {

        @Test void freshStrategyFullScore() {
            Instant justNow = Instant.now();
            double score = service.computeFreshnessScore(justNow, List.of());
            assertTrue(score >= 95, "Strategy created now should have near-100 freshness, got " + score);
        }

        @Test void halfAgeStrategy() {
            Instant fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS);
            double score = service.computeFreshnessScore(fifteenDaysAgo, List.of());
            assertTrue(score >= 45 && score <= 55, "15-day strategy should be ~50, got " + score);
        }

        @Test void expiredStrategy() {
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            double score = service.computeFreshnessScore(thirtyDaysAgo, List.of());
            assertEquals(0, score, 1.0, "30-day strategy should be near 0");
        }

        @Test void signalsPenalize() {
            Instant fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
            double withoutSignals = service.computeFreshnessScore(fiveDaysAgo, List.of());
            double withSignals = service.computeFreshnessScore(fiveDaysAgo, List.of("SIGNAL_A", "SIGNAL_B"));
            assertTrue(withSignals < withoutSignals, "Signals should reduce freshness");
            double expected = withoutSignals - 16; // 2 signals * 8 points each
            assertEquals(expected, withSignals, 1.0);
        }

        @Test void manySignalsClampsToZero() {
            Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
            double score = service.computeFreshnessScore(tenDaysAgo, List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"));
            assertEquals(0, score, 0.1, "Many signals should bottom out at 0");
        }
    }

    @Nested class DeriveActionTests {

        @Test void regenerateWhenVeryLow() {
            assertEquals("REGENERATE", service.deriveAction(20.0, List.of("A")));
        }

        @Test void regenerateWithManySignals() {
            assertEquals("REGENERATE", service.deriveAction(60.0, List.of("A", "B", "C", "D")));
        }

        @Test void refreshWhenModerate() {
            assertEquals("REFRESH", service.deriveAction(40.0, List.of("A")));
        }

        @Test void refreshWithTwoSignals() {
            assertEquals("REFRESH", service.deriveAction(70.0, List.of("A", "B")));
        }

        @Test void keepWhenHealthy() {
            assertEquals("KEEP", service.deriveAction(80.0, List.of("A")));
        }

        @Test void keepWithNoSignals() {
            assertEquals("KEEP", service.deriveAction(90.0, List.of()));
        }
    }

    @Nested class StalenessDetectionTests {

        @Test void noSignalsForFreshStrategy() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(5, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.valueOf(2.5),
                    "totalConversions", java.math.BigDecimal.valueOf(100),
                    "totalAssets", 10L,
                    "assetClassifications", Map.of("WINNER", 5L, "WEAK", 2L)
            );
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(List.of());

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.isEmpty(), "Fresh strategy with good metrics should have no signals");
        }

        @Test void detectsOldStrategy() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(20, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.valueOf(2.0),
                    "totalConversions", java.math.BigDecimal.valueOf(50),
                    "totalAssets", 10L,
                    "assetClassifications", Map.of("WINNER", 5L)
            );
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(List.of());

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.stream().anyMatch(s -> s.contains("STRATEGY_AGE")));
        }

        @Test void detectsLowRoas() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(5, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.valueOf(0.5),
                    "totalConversions", java.math.BigDecimal.valueOf(10),
                    "totalAssets", 10L,
                    "assetClassifications", Map.of()
            );
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(List.of());

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.stream().anyMatch(s -> s.contains("LOW_ROAS")));
        }

        @Test void detectsHighWeakRatio() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(5, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.valueOf(2.0),
                    "totalConversions", java.math.BigDecimal.valueOf(50),
                    "totalAssets", 10L,
                    "assetClassifications", Map.of("WEAK", 7L, "WINNER", 3L)
            );
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(List.of());

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.stream().anyMatch(s -> s.contains("HIGH_WEAK_RATIO")));
        }

        @Test void detectsZeroConversions() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(5, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.ZERO,
                    "totalConversions", java.math.BigDecimal.ZERO,
                    "totalAssets", 5L,
                    "assetClassifications", Map.of()
            );
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(List.of());

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.stream().anyMatch(s -> s.contains("ZERO_CONVERSIONS")));
        }

        @Test void detectsUnactionedRecommendations() {
            Map<String, Object> strategy = Map.of("createdAt", Instant.now().minus(5, ChronoUnit.DAYS));
            Map<String, Object> metrics = Map.of(
                    "overallRoas", java.math.BigDecimal.valueOf(2.0),
                    "totalConversions", java.math.BigDecimal.valueOf(50),
                    "totalAssets", 10L,
                    "assetClassifications", Map.of()
            );
            List<CreativeOptimizationRecommendation> openRecs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                CreativeOptimizationRecommendation rec = new CreativeOptimizationRecommendation();
                rec.setRecommendationType("SCALE");
                openRecs.add(rec);
            }
            when(recRepo.findByBusinessIdAndStatusOrderByCreatedAtDesc(eq(BIZ), eq("OPEN")))
                    .thenReturn(openRecs);

            List<String> signals = service.detectStalenessSignals(BIZ, strategy, metrics);

            assertTrue(signals.stream().anyMatch(s -> s.contains("UNACTIONED_RECOMMENDATIONS")));
        }
    }

    @Nested class GetLatestFreshnessTests {

        @Test void returnsLatest() {
            StrategyEffectiveness eff = new StrategyEffectiveness();
            eff.setId(UUID.randomUUID());
            eff.setBusinessId(BIZ);
            eff.setStrategyRunId(UUID.randomUUID());
            eff.setFreshnessScore(75.0);
            eff.setRecommendedAction("KEEP");
            eff.setStalenessSignals("[]");
            eff.setCreatedAt(Instant.now());

            when(effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of(eff));

            Map<String, Object> result = service.getLatestFreshness(BIZ);

            assertEquals(BIZ, result.get("businessId"));
            assertEquals(75.0, result.get("freshnessScore"));
            assertEquals("KEEP", result.get("recommendedAction"));
        }

        @Test void noEvaluationYet() {
            when(effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

            Map<String, Object> result = service.getLatestFreshness(BIZ);

            assertEquals(-1, result.get("freshnessScore"));
            assertEquals("EVALUATE", result.get("recommendedAction"));
        }
    }

    @Nested class GetHistoryTests {

        @Test void returnsHistory() {
            StrategyEffectiveness eff1 = new StrategyEffectiveness();
            eff1.setId(UUID.randomUUID());
            eff1.setBusinessId(BIZ);
            eff1.setStrategyRunId(UUID.randomUUID());
            eff1.setFreshnessScore(80.0);
            eff1.setRecommendedAction("KEEP");
            eff1.setStalenessSignals("[]");
            eff1.setMetricsAtEvaluation("{}");
            eff1.setEvaluationType("PERIODIC");
            eff1.setCreatedAt(Instant.now());

            when(effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of(eff1));

            List<Map<String, Object>> history = service.getHistory(BIZ);

            assertEquals(1, history.size());
        }

        @Test void emptyHistory() {
            when(effectivenessRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ)).thenReturn(List.of());

            List<Map<String, Object>> history = service.getHistory(BIZ);

            assertTrue(history.isEmpty());
        }
    }
}
