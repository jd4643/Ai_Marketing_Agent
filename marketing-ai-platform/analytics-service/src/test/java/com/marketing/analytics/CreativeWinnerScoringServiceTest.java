package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.CreativeWinnerScoringService.ScoringResult;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CreativeWinnerScoringServiceTest {

    @Mock CreativeAssetPerformanceRepository repo;
    @InjectMocks CreativeWinnerScoringService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "minImpressions", 3000L);
        ReflectionTestUtils.setField(service, "minClicks", 100L);
        ReflectionTestUtils.setField(service, "minConversions", 3L);
        ReflectionTestUtils.setField(service, "minRoas", 2.0);
        ReflectionTestUtils.setField(service, "weakMaxRoas", 0.8);
    }

    @Test void classifyWinner() {
        assertEquals("WINNER", service.classify(5000, 200, 10, 3.0));
    }

    @Test void classifyWeak() {
        assertEquals("WEAK", service.classify(4000, 50, 1, 0.5));
    }

    @Test void classifyTesting() {
        assertEquals("TESTING", service.classify(3500, 120, 5, 1.5));
    }

    @Test void classifyInsufficientData() {
        assertEquals("INSUFFICIENT_DATA", service.classify(500, 5, 0, 0.0));
    }

    @Test void performanceScoreRange() {
        BigDecimal score = service.computePerformanceScore(5000, 200, 10, 3.0, 0.04, 15.0, BigDecimal.valueOf(500));
        assertTrue(score.doubleValue() >= 0 && score.doubleValue() <= 100, "Score should be in [0,100]: " + score);
    }

    @Test void performanceScoreHighForWinner() {
        BigDecimal score = service.computePerformanceScore(10000, 500, 50, 5.0, 0.05, 10.0, BigDecimal.valueOf(500));
        assertTrue(score.doubleValue() > 50, "Winner score should be high: " + score);
    }

    @Test void performanceScoreLowForWeak() {
        BigDecimal score = service.computePerformanceScore(4000, 50, 1, 0.3, 0.0125, 800.0, BigDecimal.valueOf(800));
        assertTrue(score.doubleValue() < 40, "Weak score should be low: " + score);
    }

    @Test void confidenceRange() {
        BigDecimal conf = service.computeConfidence(5000, 200, 10, 3.0, 0.04);
        assertTrue(conf.doubleValue() >= 0 && conf.doubleValue() <= 1, "Confidence should be in [0,1]: " + conf);
    }

    @Test void confidenceHighWithVolume() {
        BigDecimal conf = service.computeConfidence(10000, 500, 50, 5.0, 0.05);
        assertTrue(conf.doubleValue() > 0.7, "High-volume confidence should be high: " + conf);
    }

    @Test void confidenceLowWithLowVolume() {
        BigDecimal conf = service.computeConfidence(100, 2, 0, 0.0, 0.02);
        assertTrue(conf.doubleValue() < 0.5, "Low-volume confidence should be low: " + conf);
    }

    @Test void buildReasoningNotEmpty() {
        Map<String, Object> reasoning = service.buildReasoning(5000, 200, 10, 3.0, "WINNER");
        assertNotNull(reasoning);
        assertEquals("WINNER", reasoning.get("classification"));
        assertNotNull(reasoning.get("summary"));
    }

    @Test void scoreAllAssetsEmpty() {
        when(repo.aggregateAllAssets(any(), any(), anyInt())).thenReturn(List.of());
        List<ScoringResult> results = service.scoreAllAssets(UUID.randomUUID(), 30, 50);
        assertTrue(results.isEmpty());
    }

    @Test void scoreAllAssetsWithData() {
        UUID assetId = UUID.randomUUID();
        // aggregateAllAssets returns 14 columns:
        // 0:creativeAssetId, 1:platform, 2:impressions, 3:clicks, 4:conversions,
        // 5:spend, 6:revenue, 7:avgRoas, 8:avgCtr, 9:avgCpc, 10:avgCpa,
        // 11:metadataJson, 12:assetType, 13:promptText
        Object[] row = new Object[]{
                assetId, "meta", 5000L, 200L, 10L,
                BigDecimal.valueOf(500), BigDecimal.valueOf(1500), BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(0.04), BigDecimal.valueOf(2.5), BigDecimal.valueOf(50.0),
                "{\"hook\":\"Test\"}", "image", "test prompt"
        };
        when(repo.aggregateAllAssets(any(), any(), anyInt())).thenReturn(List.<Object[]>of(row));

        List<ScoringResult> results = service.scoreAllAssets(UUID.randomUUID(), 30, 50);
        assertEquals(1, results.size());
        ScoringResult sr = results.get(0);
        assertEquals(assetId, sr.creativeAssetId());
        assertEquals("WINNER", sr.classification());
        assertTrue(sr.performanceScore().doubleValue() > 30);
        assertTrue(sr.confidenceScore().doubleValue() > 0.3);
    }
}
