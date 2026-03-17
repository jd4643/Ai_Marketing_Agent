package com.marketing.analytics.platform.meta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.platform.AdPlatformSyncClient;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MetaAdPlatformSyncClient implements AdPlatformSyncClient {

    private static final Logger log = LoggerFactory.getLogger(MetaAdPlatformSyncClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final OkHttpClient client;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String apiVersion;

    public MetaAdPlatformSyncClient(
            @Value("${meta.api.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${meta.api.version:v21.0}") String apiVersion,
            @Value("${meta.api.timeout-seconds:30}") int timeout) {
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String platform() { return "META"; }

    @Override
    public boolean validateConnection(String accessToken, String externalAccountId) {
        String url = baseUrl + "/" + apiVersion + "/" + externalAccountId
                + "?fields=name,account_status&access_token=" + accessToken;
        String requestId = MDC.get("requestId");
        log.info("Validating Meta connection for account={} requestId={}", externalAccountId, requestId);
        try {
            Map<String, Object> resp = executeWithRetry(url);
            return resp != null && resp.containsKey("name");
        } catch (Exception e) {
            log.warn("Meta connection validation failed for account={}: {}", externalAccountId, e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> fetchAds(String accessToken, String externalAccountId) {
        List<Map<String, Object>> allAds = new ArrayList<>();
        String url = baseUrl + "/" + apiVersion + "/" + externalAccountId
                + "/ads?fields=id,name,status,effective_status,campaign_id,adset_id,"
                + "creative{id,name,title,body},campaign{id,name,objective}"
                + "&limit=500&access_token=" + accessToken;
        String requestId = MDC.get("requestId");
        log.info("Fetching Meta ads for account={} requestId={}", externalAccountId, requestId);

        while (url != null) {
            Map<String, Object> resp = executeWithRetry(url);
            if (resp == null) break;

            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ad = (Map<String, Object>) m;
                        allAds.add(ad);
                    }
                }
            }

            url = extractNextPageUrl(resp);
        }

        log.info("Fetched {} Meta ads for account={} requestId={}", allAds.size(), externalAccountId, requestId);
        return allAds;
    }

    @Override
    public List<Map<String, Object>> fetchInsights(String accessToken, String externalAccountId, int lookbackDays) {
        List<Map<String, Object>> allInsights = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(lookbackDays);
        String url = baseUrl + "/" + apiVersion + "/" + externalAccountId
                + "/insights?fields=ad_id,impressions,clicks,spend,reach,ctr,cpc,cpm,"
                + "actions,action_values,date_start,date_stop"
                + "&level=ad&time_range={\"since\":\"" + start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + "\",\"until\":\"" + end.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\"}"
                + "&time_increment=1&limit=500&access_token=" + accessToken;
        String requestId = MDC.get("requestId");
        log.info("Fetching Meta insights for account={} lookback={} requestId={}", externalAccountId, lookbackDays, requestId);

        while (url != null) {
            Map<String, Object> resp = executeWithRetry(url);
            if (resp == null) break;

            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> insight = (Map<String, Object>) m;
                        allInsights.add(insight);
                    }
                }
            }

            url = extractNextPageUrl(resp);
        }

        log.info("Fetched {} Meta insight rows for account={} requestId={}", allInsights.size(), externalAccountId, requestId);
        return allInsights;
    }

    private Map<String, Object> executeWithRetry(String url) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Request req = new Request.Builder().url(url).get().build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        return om.readValue(resp.body().string(), new TypeReference<>() {});
                    }
                    int code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "";

                    if (code == 429 || (code >= 500 && code < 600)) {
                        log.warn("Meta API returned {} on attempt {}, will retry. body={}", code, attempt + 1, body);
                        if (attempt < MAX_RETRIES) {
                            sleepWithJitter(attempt);
                            continue;
                        }
                    }
                    log.error("Meta API error: code={} body={}", code, body);
                    return null;
                }
            } catch (IOException e) {
                log.warn("Meta API IO error on attempt {}: {}", attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleepWithJitter(attempt);
                } else {
                    log.error("Meta API call failed after {} retries", MAX_RETRIES, e);
                    return null;
                }
            }
        }
        return null;
    }

    private void sleepWithJitter(int attempt) {
        long delay = BASE_DELAY_MS * (1L << attempt) + ThreadLocalRandom.current().nextLong(500);
        try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    @SuppressWarnings("unchecked")
    private String extractNextPageUrl(Map<String, Object> resp) {
        Object paging = resp.get("paging");
        if (paging instanceof Map<?, ?> p) {
            Object next = ((Map<String, Object>) p).get("next");
            return next instanceof String s ? s : null;
        }
        return null;
    }
}
