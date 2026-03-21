package com.marketing.analytics.api;

import com.marketing.analytics.dashboard.DashboardDtos.*;
import com.marketing.analytics.service.DashboardAggregationService;

import jakarta.validation.constraints.*;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics/dashboard")
@Validated
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final DashboardAggregationService dashboardService;

    public DashboardController(DashboardAggregationService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        log.info("GET /analytics/dashboard/overview businessId={} days={}", businessId, days);
        return dashboardService.getOverview(businessId, days);
    }

    @GetMapping("/creatives")
    public CreativesResponse creatives(
            @RequestParam UUID businessId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        log.info("GET /analytics/dashboard/creatives businessId={} status={} platform={} limit={}",
                businessId, status, platform, limit);
        return dashboardService.getCreatives(businessId, status, platform, limit);
    }

    @GetMapping("/recommendations")
    public RecommendationsResponse recommendations(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "OPEN") String status) {
        log.info("GET /analytics/dashboard/recommendations businessId={} status={}", businessId, status);
        return dashboardService.getRecommendations(businessId, status);
    }

    @GetMapping("/strategy")
    public StrategyResponse strategy(@RequestParam UUID businessId) {
        log.info("GET /analytics/dashboard/strategy businessId={}", businessId);
        return dashboardService.getStrategy(businessId);
    }

    @GetMapping("/platforms")
    public PlatformsResponse platforms(
            @RequestParam UUID businessId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        log.info("GET /analytics/dashboard/platforms businessId={} days={}", businessId, days);
        return dashboardService.getPlatforms(businessId, days);
    }

    @GetMapping("/execution")
    public ExecutionOverview executionOverview(@RequestParam UUID businessId) {
        log.info("GET /analytics/dashboard/execution businessId={}", businessId);
        return dashboardService.getExecutionOverview(businessId);
    }
}
