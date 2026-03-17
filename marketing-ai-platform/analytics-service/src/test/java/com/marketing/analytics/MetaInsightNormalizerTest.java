package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.marketing.analytics.platform.AdPlatformInsightNormalizer.NormalizedInsight;
import com.marketing.analytics.platform.meta.MetaInsightNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetaInsightNormalizerTest {

    private final MetaInsightNormalizer normalizer = new MetaInsightNormalizer();

    @Test void platform() {
        assertEquals("META", normalizer.platform());
    }

    @Test void basicNormalization() {
        Map<String, Object> raw = Map.of(
                "ad_id", "123",
                "date_start", "2024-11-01",
                "date_stop", "2024-11-01",
                "impressions", "5000",
                "clicks", "200",
                "spend", "50.00",
                "reach", "4500",
                "ctr", "4.0",
                "cpc", "0.25",
                "cpm", "10.0");

        NormalizedInsight ni = normalizer.normalize(raw);
        assertEquals("123", ni.externalAdId());
        assertEquals(LocalDate.of(2024, 11, 1), ni.dateStart());
        assertEquals(5000L, ni.impressions());
        assertEquals(200L, ni.clicks());
        assertEquals(0, new BigDecimal("50.00").compareTo(ni.spend()));
    }

    @Test void extractConversionsFromActions() {
        Map<String, Object> raw = Map.of(
                "ad_id", "456",
                "date_start", "2024-11-01",
                "date_stop", "2024-11-01",
                "impressions", "1000",
                "clicks", "50",
                "spend", "25.00",
                "actions", List.of(
                    Map.of("action_type", "purchase", "value", "10"),
                    Map.of("action_type", "lead", "value", "5"),
                    Map.of("action_type", "link_click", "value", "100")));

        NormalizedInsight ni = normalizer.normalize(raw);
        assertEquals(15L, ni.conversions()); // purchase + lead, not link_click
    }

    @Test void extractRevenueAndComputeRoas() {
        Map<String, Object> raw = Map.of(
                "ad_id", "789",
                "date_start", "2024-11-01",
                "date_stop", "2024-11-01",
                "impressions", "1000",
                "clicks", "50",
                "spend", "100.00",
                "action_values", List.of(
                    Map.of("action_type", "offsite_conversion.fb_pixel_purchase", "value", "300.00"),
                    Map.of("action_type", "link_click", "value", "50.00")));

        NormalizedInsight ni = normalizer.normalize(raw);
        assertEquals(0, new BigDecimal("300.00").compareTo(ni.revenue()));
        assertEquals(0, new BigDecimal("3.0000").compareTo(ni.roas())); // 300/100 = 3
    }

    @Test void nullActionsHandled() {
        Map<String, Object> raw = Map.of(
                "ad_id", "000",
                "date_start", "2024-11-01",
                "date_stop", "2024-11-01",
                "impressions", "100",
                "clicks", "5",
                "spend", "10.00");

        NormalizedInsight ni = normalizer.normalize(raw);
        assertNull(ni.conversions());
        assertNull(ni.revenue());
        assertNull(ni.roas());
    }
}
