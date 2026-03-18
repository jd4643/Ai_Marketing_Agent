package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService.RecommendationResult;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreativeOptimizationRecommendationServiceTest {

    @Mock CreativeWinnerScoringService scoringService;
    @Mock CreativeOptimizationRecommendationRepository recRepo;
    @InjectMocks CreativeOptimizationRecommendationService service;

    @Test void generateEmptyWhenNoAssets() {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        assertTrue(recs.isEmpty());
    }

    @Test void scaleRecommendationForWinner() {
        ScoringResult winner = new ScoringResult(
                UUID.randomUUID(), "meta", "WINNER",
                BigDecimal.valueOf(85), BigDecimal.valueOf(0.92), Map.of(),
                5000L, 200L, 10L,
                BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                3.0, 0.04, 2.5, 50.0,
                null, "image", null);
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(winner));

        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        assertTrue(recs.stream().anyMatch(r -> "SCALE".equals(r.recommendationType())));
        assertTrue(recs.stream().anyMatch(r -> "DUPLICATE_WINNER".equals(r.recommendationType())));
    }

    @Test void stopRecommendationForWeak() {
        ScoringResult weak = new ScoringResult(
                UUID.randomUUID(), "meta", "WEAK",
                BigDecimal.valueOf(15), BigDecimal.valueOf(0.80), Map.of(),
                4000L, 50L, 1L,
                BigDecimal.valueOf(800), BigDecimal.valueOf(200),
                0.25, 0.0125, 16.0, 800.0,
                null, "video", null);
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(weak));

        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        assertTrue(recs.stream().anyMatch(r -> "STOP".equals(r.recommendationType())));
    }

    @Test void testMoreRecommendationForTesting() {
        ScoringResult testing = new ScoringResult(
                UUID.randomUUID(), "google", "TESTING",
                BigDecimal.valueOf(50), BigDecimal.valueOf(0.60), Map.of(),
                1500L, 30L, 2L,
                BigDecimal.valueOf(200), BigDecimal.valueOf(300),
                1.5, 0.02, 6.67, 100.0,
                null, "image", null);
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(testing));

        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        assertTrue(recs.stream().anyMatch(r -> "TEST_MORE".equals(r.recommendationType())));
    }

    @Test void multipleClassificationsMixedRecommendations() {
        ScoringResult winner = new ScoringResult(
                UUID.randomUUID(), "meta", "WINNER",
                BigDecimal.valueOf(85), BigDecimal.valueOf(0.92), Map.of(),
                5000L, 200L, 10L,
                BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                3.0, 0.04, 2.5, 50.0,
                null, "image", null);
        ScoringResult weak = new ScoringResult(
                UUID.randomUUID(), "meta", "WEAK",
                BigDecimal.valueOf(15), BigDecimal.valueOf(0.80), Map.of(),
                4000L, 50L, 1L,
                BigDecimal.valueOf(800), BigDecimal.valueOf(200),
                0.25, 0.0125, 16.0, 800.0,
                null, "video", null);
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(winner, weak));

        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        assertTrue(recs.stream().anyMatch(r -> "SCALE".equals(r.recommendationType())));
        assertTrue(recs.stream().anyMatch(r -> "STOP".equals(r.recommendationType())));
    }

    @Test void generateAndPersistClearsOldAndSaves() {
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of());
        service.generateAndPersist(UUID.randomUUID(), 30);
        verify(recRepo).deleteByBusinessIdAndStatus(any(), eq("OPEN"));
    }

    @Test void recommendationResultFields() {
        ScoringResult winner = new ScoringResult(
                UUID.randomUUID(), "meta", "WINNER",
                BigDecimal.valueOf(85), BigDecimal.valueOf(0.92), Map.of(),
                5000L, 200L, 10L,
                BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
                3.0, 0.04, 2.5, 50.0,
                null, "image", null);
        when(scoringService.scoreAllAssets(any(), anyInt(), anyInt())).thenReturn(List.of(winner));

        List<RecommendationResult> recs = service.generateRecommendations(UUID.randomUUID(), 30);
        for (RecommendationResult r : recs) {
            assertNotNull(r.recommendationType());
            assertNotNull(r.priority());
            assertNotNull(r.title());
            assertNotNull(r.description());
            assertNotNull(r.suggestedNextAction());
        }
    }
}
