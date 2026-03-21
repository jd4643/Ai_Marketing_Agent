package com.marketing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.PerformanceSnapshot;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.PerformanceSnapshotRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures point-in-time performance snapshots for trend tracking and plan evaluation.
 * Supports manual, pre-plan, and post-plan snapshot types.
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final PerformanceSnapshotRepository snapshotRepo;
    private final CampaignMetricRepository metricRepo;
    private final CreativeAssetPerformanceRepository perfRepo;
    private final ObjectMapper om = new ObjectMapper();

    public SnapshotService(PerformanceSnapshotRepository snapshotRepo,
                           CampaignMetricRepository metricRepo,
                           CreativeAssetPerformanceRepository perfRepo) {
        this.snapshotRepo = snapshotRepo;
        this.metricRepo = metricRepo;
        this.perfRepo = perfRepo;
    }

    /**
     * Capture a performance snapshot for a business.
     * Aggregates current campaign metrics and creative asset performance.
     */
    @Transactional
    public Map<String, Object> captureSnapshot(UUID businessId, Map<String, Object> request) {
        String requestId = MDC.get("requestId");
        String snapshotType = (String) request.getOrDefault("snapshotType", "MANUAL");
        String label = (String) request.get("label");
        UUID planId = parseUuid(request.get("planId"));

        // Aggregate current metrics
        Map<String, Object> metrics = aggregateCurrentMetrics(businessId);

        Instant now = Instant.now();
        PerformanceSnapshot snapshot = new PerformanceSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setBusinessId(businessId);
        snapshot.setPlanId(planId);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setLabel(label);
        snapshot.setMetricsJson(toJsonSafe(metrics));
        snapshot.setCreatedAt(now);
        snapshotRepo.save(snapshot);

        log.info("Captured {} snapshot {} for business={} requestId={}",
                snapshotType, snapshot.getId(), businessId, requestId);
        return snapshotToMap(snapshot);
    }

    /**
     * List snapshots for a business, optionally filtered by type or time range.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSnapshots(UUID businessId, String snapshotType, int days) {
        List<PerformanceSnapshot> snapshots;
        if (snapshotType != null && !snapshotType.isBlank()) {
            snapshots = snapshotRepo.findByBusinessIdAndSnapshotTypeOrderByCreatedAtDesc(businessId, snapshotType);
        } else if (days > 0) {
            Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
            snapshots = snapshotRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(businessId, since);
        } else {
            snapshots = snapshotRepo.findByBusinessIdOrderByCreatedAtDesc(businessId);
        }
        return snapshots.stream().map(this::snapshotToMap).toList();
    }

    /**
     * List snapshots associated with a specific plan.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlanSnapshots(UUID planId) {
        List<PerformanceSnapshot> snapshots = snapshotRepo.findByPlanIdOrderByCreatedAtDesc(planId);
        return snapshots.stream().map(this::snapshotToMap).toList();
    }

    /**
     * Get a single snapshot by ID.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSnapshot(UUID snapshotId) {
        PerformanceSnapshot snapshot = snapshotRepo.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
        return snapshotToMap(snapshot);
    }

    /**
     * Compare two snapshots and compute deltas.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareSnapshots(UUID baselineId, UUID currentId) {
        PerformanceSnapshot baseline = snapshotRepo.findById(baselineId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline snapshot not found: " + baselineId));
        PerformanceSnapshot current = snapshotRepo.findById(currentId)
                .orElseThrow(() -> new IllegalArgumentException("Current snapshot not found: " + currentId));

        Map<String, Object> baseMetrics = parseJsonSafe(baseline.getMetricsJson());
        Map<String, Object> currMetrics = parseJsonSafe(current.getMetricsJson());

        Map<String, Object> deltas = computeDeltas(baseMetrics, currMetrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baselineId", baselineId);
        result.put("baselineCreatedAt", baseline.getCreatedAt());
        result.put("currentId", currentId);
        result.put("currentCreatedAt", current.getCreatedAt());
        result.put("baseline", baseMetrics);
        result.put("current", currMetrics);
        result.put("deltas", deltas);
        return result;
    }

    // ─── Metrics Aggregation ────────────────────────────────────────────────

    private Map<String, Object> aggregateCurrentMetrics(UUID businessId) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Campaign-level aggregation (last 30 days)
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        try {
            List<Object[]> campaignAgg = metricRepo.aggregateByBusinessId(businessId, since);
            if (campaignAgg != null && !campaignAgg.isEmpty()) {
                Object[] row = campaignAgg.get(0);
                metrics.put("totalSpend", toBd(row[0]));
                metrics.put("totalRevenue", toBd(row[1]));
                metrics.put("totalImpressions", toLng(row[2]));
                metrics.put("totalClicks", toLng(row[3]));
                metrics.put("totalConversions", toLng(row[4]));

                BigDecimal spend = toBd(row[0]);
                BigDecimal revenue = toBd(row[1]);
                metrics.put("overallRoas", spend.compareTo(BigDecimal.ZERO) > 0
                        ? revenue.divide(spend, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);

                long impressions = toLng(row[2]);
                long clicks = toLng(row[3]);
                metrics.put("ctr", impressions > 0
                        ? BigDecimal.valueOf(clicks).divide(BigDecimal.valueOf(impressions), 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("Failed to aggregate campaign metrics for business={}: {}", businessId, e.getMessage());
            metrics.put("totalSpend", BigDecimal.ZERO);
            metrics.put("totalRevenue", BigDecimal.ZERO);
            metrics.put("totalImpressions", 0L);
            metrics.put("totalClicks", 0L);
            metrics.put("totalConversions", 0L);
            metrics.put("overallRoas", BigDecimal.ZERO);
            metrics.put("ctr", BigDecimal.ZERO);
        }

        // Creative asset counts by classification
        try {
            List<Object[]> classificationCounts = perfRepo.countByClassification(businessId);
            Map<String, Long> classifications = new LinkedHashMap<>();
            long totalAssets = 0;
            if (classificationCounts != null) {
                for (Object[] row : classificationCounts) {
                    String cls = row[0] != null ? row[0].toString() : "UNKNOWN";
                    long cnt = toLng(row[1]);
                    classifications.put(cls, cnt);
                    totalAssets += cnt;
                }
            }
            metrics.put("totalAssets", totalAssets);
            metrics.put("assetClassifications", classifications);
        } catch (Exception e) {
            log.warn("Failed to count asset classifications for business={}: {}", businessId, e.getMessage());
            metrics.put("totalAssets", 0L);
            metrics.put("assetClassifications", Map.of());
        }

        metrics.put("capturedAt", Instant.now());
        return metrics;
    }

    private Map<String, Object> computeDeltas(Map<String, Object> base, Map<String, Object> current) {
        Map<String, Object> deltas = new LinkedHashMap<>();
        Set<String> numericKeys = Set.of("totalSpend", "totalRevenue", "totalImpressions",
                "totalClicks", "totalConversions", "overallRoas", "ctr", "totalAssets");

        for (String key : numericKeys) {
            BigDecimal baseVal = toBd(base.get(key));
            BigDecimal currVal = toBd(current.get(key));
            BigDecimal delta = currVal.subtract(baseVal);
            BigDecimal pctChange = baseVal.compareTo(BigDecimal.ZERO) != 0
                    ? delta.divide(baseVal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : (currVal.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("baseline", baseVal);
            entry.put("current", currVal);
            entry.put("delta", delta);
            entry.put("percentChange", pctChange);
            deltas.put(key, entry);
        }
        return deltas;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> snapshotToMap(PerformanceSnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("businessId", s.getBusinessId());
        m.put("planId", s.getPlanId());
        m.put("snapshotType", s.getSnapshotType());
        m.put("label", s.getLabel());
        m.put("metrics", parseJsonSafe(s.getMetricsJson()));
        m.put("insights", parseJsonSafe(s.getInsightsJson()));
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    private UUID parseUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJsonSafe(Object obj) {
        if (obj == null) return null;
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return om.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BigDecimal toBd(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private long toLng(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
