package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class CreativeAssetPerformanceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository repo;

    @Test void ingestValidation() throws Exception {
        mvc.perform(post("/analytics/creative-assets/metrics/ingest")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void summaryOk() throws Exception {
        when(repo.aggregateByAsset(any(), any())).thenReturn(List.of());
        mvc.perform(get("/analytics/creative-assets/summary")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assets").isArray());
    }

    @Test void winnersOk() throws Exception {
        when(repo.findWinners(any(), any(), anyLong(), anyInt())).thenReturn(new ArrayList<>());
        mvc.perform(get("/analytics/creative-assets/winners")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.winners").isArray())
            .andExpect(jsonPath("$.thresholds").exists());
    }

    @Test void insightsOk() throws Exception {
        when(repo.findWinners(any(), any(), anyLong(), anyInt())).thenReturn(new ArrayList<>());
        mvc.perform(get("/analytics/creative-assets/insights")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAssets").value(0))
            .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test void winnersWithData() throws Exception {
        // creative_asset_id, platform, impressions, clicks, conversions, spend, revenue, avg_roas, metadata_json, asset_type, prompt_text
        Object[] winner = new Object[]{
            UUID.randomUUID(), "meta", 5000L, 200L, 10L,
            BigDecimal.valueOf(500), BigDecimal.valueOf(1500), BigDecimal.valueOf(3.0),
            "{\"hook\":\"Stop scrolling\"}", "image", "test prompt"
        };
        List<Object[]> winnerList = new ArrayList<>();
        winnerList.add(winner);
        when(repo.findWinners(any(), any(), anyLong(), anyInt())).thenReturn(winnerList);
        mvc.perform(get("/analytics/creative-assets/winners")
                .param("businessId", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.winners[0].winnerStatus").value("WINNER"));
    }
}
