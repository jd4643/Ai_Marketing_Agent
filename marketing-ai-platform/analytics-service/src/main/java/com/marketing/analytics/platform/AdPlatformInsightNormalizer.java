package com.marketing.analytics.platform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Normalizes raw platform insight data into a standard shape for ad_platform_insights.
 */
public interface AdPlatformInsightNormalizer {

    String platform();

    record NormalizedInsight(
        String externalAdId,
        LocalDate dateStart,
        LocalDate dateStop,
        Long impressions,
        Long clicks,
        BigDecimal spend,
        Long reach,
        BigDecimal ctr,
        BigDecimal cpc,
        BigDecimal cpm,
        Long conversions,
        BigDecimal revenue,
        BigDecimal roas,
        String actionsJson,
        String actionValuesJson,
        String rawJson
    ) {}

    NormalizedInsight normalize(Map<String, Object> raw);
}
