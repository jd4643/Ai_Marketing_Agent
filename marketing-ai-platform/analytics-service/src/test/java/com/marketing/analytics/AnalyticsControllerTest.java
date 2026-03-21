package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.model.CampaignMetric;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.AdPlatformIntegrationService;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.CreativeWinnerScoringService;
import com.marketing.analytics.service.RecommendationActionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class AnalyticsControllerTest {
  @Autowired MockMvc mvc;
  @MockBean CampaignMetricRepository repo;
  @MockBean CreativeAssetPerformanceRepository capRepo;
  @MockBean CreativeOptimizationRecommendationRepository recRepo;
  @MockBean AdPlatformIntegrationService integrationService;
  @MockBean CreativeWinnerScoringService scoringService;
  @MockBean CreativeOptimizationRecommendationService recommendationService;
  @MockBean RecommendationActionService actionService;
  @MockBean com.marketing.analytics.service.DashboardAggregationService dashboardAggregationService;
  @MockBean com.marketing.analytics.service.ExecutionService executionService;
  @MockBean com.marketing.analytics.service.SnapshotService snapshotService;

  @Test void ingestValidation() throws Exception {
    mvc.perform(post("/analytics/metrics/ingest").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test void summaryOk() throws Exception {
    when(repo.aggregate(any(), any())).thenReturn(List.of());
    mvc.perform(get("/analytics/summary").param("businessId", UUID.randomUUID().toString()))
        .andExpect(status().isOk());
  }
}
