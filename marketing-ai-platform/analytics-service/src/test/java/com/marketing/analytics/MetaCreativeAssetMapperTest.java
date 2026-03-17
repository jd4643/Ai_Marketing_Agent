package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.marketing.analytics.model.AdPlatformAd;
import com.marketing.analytics.model.CreativeAssetPlatformMapping;
import com.marketing.analytics.platform.meta.MetaCreativeAssetMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class MetaCreativeAssetMapperTest {

    private final MetaCreativeAssetMapper mapper = new MetaCreativeAssetMapper();

    private AdPlatformAd ad(String adName, String externalAdId, String creativeName) {
        AdPlatformAd ad = new AdPlatformAd();
        ad.setExternalAdId(externalAdId);
        ad.setAdName(adName);
        ad.setCreativeName(creativeName);
        ad.setExternalCreativeId("cr_" + externalAdId);
        return ad;
    }

    private Map<String, Object> asset(UUID id, String conceptName, String hook) {
        Map<String, Object> a = new HashMap<>();
        a.put("id", id.toString());
        String meta = "{\"conceptName\":\"%s\",\"hook\":\"%s\"}".formatted(
                conceptName != null ? conceptName : "", hook != null ? hook : "");
        a.put("metadataJson", meta);
        return a;
    }

    @Test void platform() {
        assertEquals("META", mapper.platform());
    }

    @Test void metadataMatch() {
        UUID assetId = UUID.randomUUID();
        List<CreativeAssetPlatformMapping> result = mapper.mapAssets(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(ad("SummerSale campaign", "ad1", "creative1")),
                List.of(asset(assetId, "SummerSale", "Get deals now")));

        assertEquals(1, result.size());
        assertEquals("METADATA_MATCH", result.get(0).getMappingMethod());
        assertTrue(result.get(0).getConfidenceScore().doubleValue() >= 0.9);
        assertEquals(assetId, result.get(0).getCreativeAssetId());
    }

    @Test void hookNameMatch() {
        UUID assetId = UUID.randomUUID();
        List<CreativeAssetPlatformMapping> result = mapper.mapAssets(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(ad("Stop scrolling great deals inside", "ad2", "creative2")),
                List.of(asset(assetId, "UnrelatedConcept", "Stop scrolling great deals")));

        assertEquals(1, result.size());
        assertEquals("NAME_MATCH", result.get(0).getMappingMethod());
        assertTrue(result.get(0).getConfidenceScore().doubleValue() >= 0.5);
    }

    @Test void belowThresholdNotMapped() {
        List<CreativeAssetPlatformMapping> result = mapper.mapAssets(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(ad("Totally different ad", "ad3", "creative3")),
                List.of(asset(UUID.randomUUID(), "NoMatch", "Unrelated hook words")));

        assertEquals(0, result.size());
    }

    @Test void emptyInputs() {
        List<CreativeAssetPlatformMapping> result = mapper.mapAssets(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());
        assertTrue(result.isEmpty());
    }

    @Test void bestMatchWins() {
        UUID weakId = UUID.randomUUID();
        UUID strongId = UUID.randomUUID();
        List<CreativeAssetPlatformMapping> result = mapper.mapAssets(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(ad("BlackFriday promotion", "ad4", "creative4")),
                List.of(
                    asset(weakId, "UnrelatedConcept", "some hook"),
                    asset(strongId, "BlackFriday", "incredible savings")));

        assertEquals(1, result.size());
        assertEquals(strongId, result.get(0).getCreativeAssetId());
        assertEquals("METADATA_MATCH", result.get(0).getMappingMethod());
    }
}
