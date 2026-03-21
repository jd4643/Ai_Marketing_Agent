package com.marketing.analytics.api;

import com.marketing.analytics.service.SnapshotService;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/snapshots")
@Validated
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);
    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public Map<String, Object> captureSnapshot(
            @RequestParam @NotNull UUID businessId,
            @RequestBody(required = false) Map<String, Object> request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("POST /analytics/snapshots businessId={}", businessId);
        return snapshotService.captureSnapshot(businessId, request != null ? request : Map.of());
    }

    @GetMapping
    public List<Map<String, Object>> listSnapshots(
            @RequestParam @NotNull UUID businessId,
            @RequestParam(required = false) String snapshotType,
            @RequestParam(defaultValue = "90") @Min(1) @Max(365) int days) {
        log.info("GET /analytics/snapshots businessId={} type={} days={}", businessId, snapshotType, days);
        return snapshotService.listSnapshots(businessId, snapshotType, days);
    }

    @GetMapping("/{snapshotId}")
    public Map<String, Object> getSnapshot(@PathVariable UUID snapshotId) {
        log.info("GET /analytics/snapshots/{}", snapshotId);
        return snapshotService.getSnapshot(snapshotId);
    }

    @GetMapping("/plan/{planId}")
    public List<Map<String, Object>> planSnapshots(@PathVariable UUID planId) {
        log.info("GET /analytics/snapshots/plan/{}", planId);
        return snapshotService.listPlanSnapshots(planId);
    }

    @GetMapping("/compare")
    public Map<String, Object> compareSnapshots(
            @RequestParam @NotNull UUID baselineId,
            @RequestParam @NotNull UUID currentId) {
        log.info("GET /analytics/snapshots/compare baseline={} current={}", baselineId, currentId);
        return snapshotService.compareSnapshots(baselineId, currentId);
    }
}
