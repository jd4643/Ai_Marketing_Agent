package com.marketing.strategy.service.intel;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ConfidenceScorer {
    public record ConfidenceResult(int confidenceScore, Map<String, Object> breakdown) {
    }

    public ConfidenceResult score(String motion, String objective, int trendsCount, long conversions, boolean coldStart) {
        double margin = 5;
        double competition = 6;
        double offer = 5;
        double marketDemand = trendsCount >= 5 ? 7 : 5;
        double channelFit = ("ONLINE".equals(motion) && ("sales".equals(objective) || "leads".equals(objective))) ? 7 : 6;
        double trust = (!"OFFLINE".equals(motion) && conversions > 0) ? 8 : conversions > 0 ? 6 : 5;

        // Cold start: force conservative trust and channel-fit — no data to validate
        if (coldStart) {
            trust = 4;
            channelFit = 5;
        }

        double weighted = margin * 1.5 + competition * 1.5 + offer * 2.0 + marketDemand * 2.0 + channelFit * 1.5 + trust * 1.5;
        int score = Math.max(0, Math.min(100, (int) Math.round(weighted)));

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("margin_strength", Map.of("raw", margin, "weight", 15));
        breakdown.put("competition_density", Map.of("raw", competition, "weight", 15));
        breakdown.put("offer_strength", Map.of("raw", offer, "weight", 20));
        breakdown.put("market_demand", Map.of("raw", marketDemand, "weight", 20));
        breakdown.put("channel_fit", Map.of("raw", channelFit, "weight", 15));
        breakdown.put("trust_level", Map.of("raw", trust, "weight", 15));
        if (coldStart) {
            breakdown.put("cold_start", Map.of("applied", true, "note", "trust and channel_fit capped due to no historical data"));
        }
        return new ConfidenceResult(score, breakdown);
    }
}
