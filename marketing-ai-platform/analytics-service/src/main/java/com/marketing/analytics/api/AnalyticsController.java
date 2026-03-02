package com.marketing.analytics.api;

import com.marketing.analytics.model.CampaignMetric;
import com.marketing.analytics.repo.CampaignMetricRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private final CampaignMetricRepository repo;

    public AnalyticsController(CampaignMetricRepository repo) {
        this.repo = repo;
    }

    public record IngestRequest(@NotNull UUID businessId, @NotBlank String platform, @NotNull BigDecimal spend,
                                Long impressions, Long clicks, Long conversions, BigDecimal revenue, BigDecimal ctr,
                                BigDecimal cpc, BigDecimal cpa, BigDecimal roas, @NotNull Instant recordedAt) {
    }

    @PostMapping("/metrics/ingest")
    public Map<String, Object> ingest(@Valid @RequestBody IngestRequest r) {
        CampaignMetric m = new CampaignMetric();
        m.setId(UUID.randomUUID());
        m.setBusinessId(r.businessId());
        m.setPlatform(r.platform());
        m.setSpend(r.spend());
        m.setImpressions(r.impressions());
        m.setClicks(r.clicks());
        m.setConversions(r.conversions());
        m.setRevenue(r.revenue());
        m.setCtr(r.ctr());
        m.setCpc(r.cpc());
        m.setCpa(r.cpa());
        m.setRoas(r.roas());
        m.setRecordedAt(r.recordedAt());
        repo.save(m);
        return Map.of("status", "OK", "id", m.getId());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam UUID businessId, @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        List<Object[]> rows = repo.aggregate(businessId, Instant.now().minus(days, ChronoUnit.DAYS));
        List<Map<String, Object>> platformSummary = rows.stream().map(r -> Map.<String, Object>of("platform", r[0], "spend", r[1], "impressions", r[2], "clicks", r[3], "conversions", r[4], "revenue", r[5], "avgRoas", r[6])).toList();
        return Map.of("businessId", businessId, "days", days, "platforms", platformSummary);
    }
}
