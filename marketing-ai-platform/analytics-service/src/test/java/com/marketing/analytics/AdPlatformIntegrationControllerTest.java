package com.marketing.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.analytics.model.AdPlatformConnection;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.service.AdPlatformIntegrationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class AdPlatformIntegrationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignMetricRepository campaignRepo;
    @MockBean CreativeAssetPerformanceRepository perfRepo;
    @MockBean AdPlatformIntegrationService service;

    // ─── Connect ────────────────────────────────────────────────────────

    @Test void connectMissingFields() throws Exception {
        mvc.perform(post("/analytics/integrations/meta/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void connectMissingToken() throws Exception {
        String json = """
            {"businessId":"%s","metaAdAccountId":"act_123"}
            """.formatted(UUID.randomUUID());
        mvc.perform(post("/analytics/integrations/meta/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test void connectOk() throws Exception {
        UUID bizId = UUID.randomUUID();
        UUID connId = UUID.randomUUID();
        AdPlatformConnection conn = new AdPlatformConnection();
        conn.setId(connId);
        conn.setPlatform("META");
        conn.setStatus("ACTIVE");
        conn.setExternalAccountId("act_123");

        when(service.connect(eq(bizId), eq("act_123"), any(), eq("tok"), any()))
                .thenReturn(conn);

        String json = """
            {"businessId":"%s","metaAdAccountId":"act_123","accessToken":"tok"}
            """.formatted(bizId);
        mvc.perform(post("/analytics/integrations/meta/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.connectionId").value(connId.toString()))
            .andExpect(jsonPath("$.platform").value("META"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ─── List ───────────────────────────────────────────────────────────

    @Test void listOk() throws Exception {
        UUID bizId = UUID.randomUUID();
        AdPlatformConnection c = new AdPlatformConnection();
        c.setId(UUID.randomUUID());
        c.setPlatform("META");
        c.setExternalAccountId("act_456");
        c.setStatus("ACTIVE");
        when(service.listConnections(bizId, "META")).thenReturn(List.of(c));

        mvc.perform(get("/analytics/integrations/meta").param("businessId", bizId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connections").isArray())
            .andExpect(jsonPath("$.connections[0].platform").value("META"));
    }

    // ─── Disconnect ─────────────────────────────────────────────────────

    @Test void disconnectOk() throws Exception {
        UUID connId = UUID.randomUUID();
        AdPlatformConnection c = new AdPlatformConnection();
        c.setId(connId);
        c.setStatus("DISCONNECTED");
        when(service.disconnect(connId)).thenReturn(c);

        mvc.perform(post("/analytics/integrations/meta/" + connId + "/disconnect"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISCONNECTED"));
    }

    // ─── Sync ───────────────────────────────────────────────────────────

    @Test void syncOk() throws Exception {
        UUID connId = UUID.randomUUID();
        when(service.sync(connId)).thenReturn(Map.of("adsSynced", 5, "insightsSynced", 100));
        mvc.perform(post("/analytics/integrations/meta/" + connId + "/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.adsSynced").value(5));
    }

    // ─── Sync Status ────────────────────────────────────────────────────

    @Test void syncStatusOk() throws Exception {
        UUID connId = UUID.randomUUID();
        when(service.syncStatus(connId)).thenReturn(Map.of("status", "ACTIVE", "adCount", 10));
        mvc.perform(get("/analytics/integrations/meta/" + connId + "/sync-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ─── Insights ───────────────────────────────────────────────────────

    @Test void insightsOk() throws Exception {
        UUID connId = UUID.randomUUID();
        when(service.insightsSummary(connId, 30))
                .thenReturn(Map.of("totalSpend", 1000, "totalImpressions", 50000));
        mvc.perform(get("/analytics/integrations/meta/" + connId + "/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpend").value(1000));
    }

    @Test void insightsCustomDays() throws Exception {
        UUID connId = UUID.randomUUID();
        when(service.insightsSummary(connId, 7)).thenReturn(Map.of("totalSpend", 200));
        mvc.perform(get("/analytics/integrations/meta/" + connId + "/insights")
                .param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpend").value(200));
    }
}
