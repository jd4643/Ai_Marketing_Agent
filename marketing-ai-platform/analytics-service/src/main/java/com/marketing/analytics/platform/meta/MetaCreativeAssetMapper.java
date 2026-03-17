package com.marketing.analytics.platform.meta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.AdPlatformAd;
import com.marketing.analytics.model.CreativeAssetPlatformMapping;
import com.marketing.analytics.platform.AdPlatformAssetMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MetaCreativeAssetMapper implements AdPlatformAssetMapper {

    private static final Logger log = LoggerFactory.getLogger(MetaCreativeAssetMapper.class);
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public String platform() { return "META"; }

    @Override
    public List<CreativeAssetPlatformMapping> mapAssets(
            UUID businessId, UUID connectionId,
            List<AdPlatformAd> externalAds,
            List<java.util.Map<String, Object>> internalAssets) {

        List<CreativeAssetPlatformMapping> mappings = new ArrayList<>();

        for (AdPlatformAd ad : externalAds) {
            String bestMethod = null;
            UUID bestAssetId = null;
            double bestScore = 0;
            java.util.Map<String, Object> matchMeta = null;

            for (java.util.Map<String, Object> asset : internalAssets) {
                UUID assetId = UUID.fromString(asset.get("id").toString());
                String metadataStr = asset.get("metadataJson") != null ? asset.get("metadataJson").toString() : null;
                java.util.Map<String, Object> metadata = parseJson(metadataStr);

                double score = 0;
                String method = "UNKNOWN";

                // Priority 1: Explicit metadata match (conceptName in ad name)
                String conceptName = metadata != null ? strVal(metadata, "conceptName") : null;
                if (conceptName != null && ad.getAdName() != null
                        && ad.getAdName().toLowerCase().contains(conceptName.toLowerCase())) {
                    score = 0.9;
                    method = "METADATA_MATCH";
                }

                // Priority 2: Name-based match (ad name contains asset concept or hook)
                if (score < 0.7) {
                    String hook = metadata != null ? strVal(metadata, "hook") : null;
                    if (hook != null && ad.getAdName() != null) {
                        String adNameLower = ad.getAdName().toLowerCase();
                        String hookLower = hook.toLowerCase();
                        String[] hookWords = hookLower.split("\\s+");
                        int matchCount = 0;
                        for (String word : hookWords) {
                            if (word.length() > 3 && adNameLower.contains(word)) matchCount++;
                        }
                        if (hookWords.length > 0) {
                            double nameScore = (double) matchCount / hookWords.length;
                            if (nameScore > 0.4) {
                                score = Math.max(score, 0.5 + nameScore * 0.3);
                                method = "NAME_MATCH";
                            }
                        }
                    }
                }

                // Priority 3: Creative metadata similarity (headline/CTA match)
                if (score < 0.5 && metadata != null) {
                    String headline = strVal(metadata, "headline");
                    String cta = strVal(metadata, "cta");
                    String creativeName = ad.getCreativeName();
                    if (creativeName != null) {
                        String creativeNameLower = creativeName.toLowerCase();
                        if (headline != null && creativeNameLower.contains(headline.toLowerCase())) {
                            score = Math.max(score, 0.6);
                            method = "METADATA_MATCH";
                        } else if (cta != null && creativeNameLower.contains(cta.toLowerCase())) {
                            score = Math.max(score, 0.4);
                            method = "METADATA_MATCH";
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestMethod = method;
                    bestAssetId = assetId;
                    matchMeta = java.util.Map.of(
                            "adName", ad.getAdName() != null ? ad.getAdName() : "",
                            "matchedAssetConcept", conceptName != null ? conceptName : "",
                            "score", score);
                }
            }

            if (bestAssetId != null && bestScore >= 0.3) {
                CreativeAssetPlatformMapping m = new CreativeAssetPlatformMapping();
                m.setId(UUID.randomUUID());
                m.setBusinessId(businessId);
                m.setCreativeAssetId(bestAssetId);
                m.setConnectionId(connectionId);
                m.setPlatform("META");
                m.setExternalAdId(ad.getExternalAdId());
                m.setExternalCreativeId(ad.getExternalCreativeId());
                m.setMappingMethod(bestMethod);
                m.setConfidenceScore(BigDecimal.valueOf(bestScore));
                m.setMetadataJson(toJson(matchMeta));
                m.setCreatedAt(Instant.now());
                mappings.add(m);
                log.debug("Mapped Meta ad {} -> asset {} method={} score={}",
                        ad.getExternalAdId(), bestAssetId, bestMethod, bestScore);
            }
        }

        log.info("Mapped {} of {} Meta ads to internal creative assets", mappings.size(), externalAds.size());
        return mappings;
    }

    private java.util.Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return om.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return null; }
    }

    private String strVal(java.util.Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return om.writeValueAsString(obj); } catch (Exception e) { return null; }
    }
}
