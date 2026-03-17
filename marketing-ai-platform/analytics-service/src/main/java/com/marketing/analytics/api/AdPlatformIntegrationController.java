package com.marketing.analytics.api;

import com.marketing.analytics.model.AdPlatformConnection;
import com.marketing.analytics.service.AdPlatformIntegrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/integrations")
public class AdPlatformIntegrationController {

    private final AdPlatformIntegrationService service;

    public AdPlatformIntegrationController(AdPlatformIntegrationService service) {
        this.service = service;
    }

    // ─── Connect ────────────────────────────────────────────────────────

    public record ConnectRequest(
            @NotNull UUID businessId,
            @NotBlank String metaAdAccountId,
            String connectionName,
            @NotBlank String accessToken,
            String metaBusinessId) {}

    @PostMapping("/meta/connect")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> connect(@Valid @RequestBody ConnectRequest req) {
        AdPlatformConnection conn = service.connect(
                req.businessId(), req.metaAdAccountId(),
                req.connectionName(), req.accessToken(), req.metaBusinessId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", MDC.get("requestId"));
        result.put("connectionId", conn.getId());
        result.put("platform", conn.getPlatform());
        result.put("status", conn.getStatus());
        result.put("externalAccountId", conn.getExternalAccountId());
        return result;
    }

    // ─── List Connections ───────────────────────────────────────────────

    @GetMapping("/meta")
    public Map<String, Object> list(@RequestParam UUID businessId) {
        List<AdPlatformConnection> connections = service.listConnections(businessId, "META");
        List<Map<String, Object>> items = connections.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("connectionId", c.getId());
            m.put("platform", c.getPlatform());
            m.put("externalAccountId", c.getExternalAccountId());
            m.put("connectionName", c.getConnectionName());
            m.put("status", c.getStatus());
            m.put("lastSyncedAt", c.getLastSyncedAt());
            return m;
        }).toList();
        return Map.of("businessId", businessId, "connections", items);
    }

    // ─── Disconnect ─────────────────────────────────────────────────────

    @PostMapping("/meta/{connectionId}/disconnect")
    public Map<String, Object> disconnect(@PathVariable UUID connectionId) {
        AdPlatformConnection conn = service.disconnect(connectionId);
        return Map.of(
                "requestId", MDC.get("requestId") != null ? MDC.get("requestId") : "",
                "connectionId", conn.getId(),
                "status", conn.getStatus());
    }

    // ─── Sync ───────────────────────────────────────────────────────────

    @PostMapping("/meta/{connectionId}/sync")
    public Map<String, Object> sync(@PathVariable UUID connectionId) {
        return service.sync(connectionId);
    }

    // ─── Sync Status ────────────────────────────────────────────────────

    @GetMapping("/meta/{connectionId}/sync-status")
    public Map<String, Object> syncStatus(@PathVariable UUID connectionId) {
        return service.syncStatus(connectionId);
    }

    // ─── Meta Insights Summary ──────────────────────────────────────────

    @GetMapping("/meta/{connectionId}/insights")
    public Map<String, Object> insights(
            @PathVariable UUID connectionId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return service.insightsSummary(connectionId, days);
    }
}
