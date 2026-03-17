package com.marketing.analytics.platform.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.platform.AdPlatformInsightNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MetaInsightNormalizer implements AdPlatformInsightNormalizer {

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public String platform() { return "META"; }

    @Override
    public NormalizedInsight normalize(Map<String, Object> raw) {
        String adId = str(raw, "ad_id");
        LocalDate dateStart = LocalDate.parse(str(raw, "date_start"));
        LocalDate dateStop = LocalDate.parse(str(raw, "date_stop"));
        Long impressions = longVal(raw, "impressions");
        Long clicks = longVal(raw, "clicks");
        BigDecimal spend = decimal(raw, "spend");
        Long reach = longVal(raw, "reach");
        BigDecimal ctr = decimal(raw, "ctr");
        BigDecimal cpc = decimal(raw, "cpc");
        BigDecimal cpm = decimal(raw, "cpm");

        Long conversions = extractConversions(raw);
        BigDecimal revenue = extractRevenue(raw);
        BigDecimal roas = spend != null && spend.signum() > 0 && revenue != null
                ? revenue.divide(spend, 4, BigDecimal.ROUND_HALF_UP)
                : null;

        String actionsJson = toJson(raw.get("actions"));
        String actionValuesJson = toJson(raw.get("action_values"));
        String rawJson = toJson(raw);

        return new NormalizedInsight(adId, dateStart, dateStop, impressions, clicks, spend, reach,
                ctr, cpc, cpm, conversions, revenue, roas, actionsJson, actionValuesJson, rawJson);
    }

    @SuppressWarnings("unchecked")
    private Long extractConversions(Map<String, Object> raw) {
        Object actions = raw.get("actions");
        if (!(actions instanceof List<?> list)) return null;
        long total = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> action = (Map<String, Object>) m;
                String type = str(action, "action_type");
                if (type != null && (type.contains("purchase") || type.contains("complete_registration")
                        || type.contains("lead") || type.contains("add_to_cart"))) {
                    total += longVal(action, "value") != null ? longVal(action, "value") : 0;
                }
            }
        }
        return total > 0 ? total : null;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractRevenue(Map<String, Object> raw) {
        Object actionValues = raw.get("action_values");
        if (!(actionValues instanceof List<?> list)) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> av = (Map<String, Object>) m;
                String type = str(av, "action_type");
                if (type != null && type.contains("purchase")) {
                    BigDecimal val = decimal(av, "value");
                    if (val != null) total = total.add(val);
                }
            }
        }
        return total.signum() > 0 ? total : null;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private Long longVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal decimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return om.writeValueAsString(obj); } catch (JsonProcessingException e) { return null; }
    }
}
