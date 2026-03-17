package com.marketing.analytics.platform;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic contract for syncing ads and insights from any ad platform.
 * Meta is the first implementation; Google/TikTok follow the same interface.
 */
public interface AdPlatformSyncClient {

    String platform();

    boolean validateConnection(String accessToken, String externalAccountId);

    List<Map<String, Object>> fetchAds(String accessToken, String externalAccountId);

    List<Map<String, Object>> fetchInsights(String accessToken, String externalAccountId, int lookbackDays);
}
