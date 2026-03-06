package com.marketing.strategy.service.intel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DecisionTreeSelector {
    public record SelectionResult(String templateKey, List<Map<String, Object>> decisionPath, String motion,
                                  String priceTier, String budgetTier) {
    }

    public SelectionResult select(Map<String, Object> business, String objective, BigDecimal monthlyBudget, boolean coldStart) {
        List<Map<String, Object>> path = new ArrayList<>();
        String website = business.get("websiteUrl") == null ? "" : String.valueOf(business.get("websiteUrl"));
        String motion = website.isBlank() ? "OFFLINE" : "ONLINE";
        path.add(Map.of("node", "motion", "value", motion));

        String priceRange = business.get("priceRange") == null ? "" : String.valueOf(business.get("priceRange")).toLowerCase(Locale.ROOT);
        String priceTier = inferPriceTier(priceRange);
        path.add(Map.of("node", "priceTier", "value", priceTier));

        String budgetTier = monthlyBudget.compareTo(new BigDecimal("500")) < 0 ? "LOW" :
                monthlyBudget.compareTo(new BigDecimal("2000")) <= 0 ? "MEDIUM" : "HIGH";
        path.add(Map.of("node", "budgetTier", "value", budgetTier));

        String objectiveNorm = objective == null ? "sales" : objective.toLowerCase(Locale.ROOT);
        path.add(Map.of("node", "objective", "value", objectiveNorm));

        if (coldStart) {
            path.add(Map.of("node", "coldStart", "value", true));
        }

        String key;
        if ("OFFLINE".equals(motion)) {
            if (coldStart) {
                // Cold start: prefer broad mid-ticket template to gather initial data
                key = "OFFLINE_MID_TICKET_FESTIVAL_FOOTFALL";
            } else if ("HIGH".equals(priceTier)) key = "OFFLINE_HIGH_TICKET_TRUST_GOOGLE_LOCAL";
            else if ("LOW".equals(priceTier) || "awareness".equals(objectiveNorm) || "traffic".equals(objectiveNorm))
                key = "OFFLINE_LOW_TICKET_IMPULSE_PROMO";
            else key = "OFFLINE_MID_TICKET_FESTIVAL_FOOTFALL";
        } else {
            if (coldStart) {
                // Cold start: prefer balanced mid-funnel template for data collection
                key = "ONLINE_MID_TICKET_META_FUNNEL_RETARGET";
            } else if ("leads".equals(objectiveNorm) || "HIGH".equals(priceTier))
                key = "ONLINE_HIGH_TICKET_LEADGEN_APPOINTMENT";
            else if ("LOW".equals(priceTier) || "awareness".equals(objectiveNorm) || "traffic".equals(objectiveNorm))
                key = "ONLINE_LOW_TICKET_UGC_BUNDLES";
            else key = "ONLINE_MID_TICKET_META_FUNNEL_RETARGET";
        }
        path.add(Map.of("node", "selectedTemplate", "value", key));
        return new SelectionResult(key, path, motion, priceTier, budgetTier);
    }

    private String inferPriceTier(String raw) {
        if (raw.contains("low") || raw.contains("cheap") || raw.contains("under")) return "LOW";
        if (raw.contains("premium") || raw.contains("lux") || raw.contains("diamond") || raw.contains("high"))
            return "HIGH";
        return "MID";
    }
}
