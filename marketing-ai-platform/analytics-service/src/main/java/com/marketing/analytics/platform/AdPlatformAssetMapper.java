package com.marketing.analytics.platform;

import com.marketing.analytics.model.AdPlatformAd;
import com.marketing.analytics.model.CreativeAssetPlatformMapping;
import java.util.List;
import java.util.UUID;

/**
 * Maps external ads to internal creative assets.
 * Implementations provide platform-specific matching logic.
 */
public interface AdPlatformAssetMapper {

    String platform();

    List<CreativeAssetPlatformMapping> mapAssets(
        UUID businessId,
        UUID connectionId,
        List<AdPlatformAd> externalAds,
        List<java.util.Map<String, Object>> internalAssets
    );
}
