package com.marketing.analytics.repo;

import com.marketing.analytics.model.AdPlatformInsight;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdPlatformInsightRepository extends JpaRepository<AdPlatformInsight, UUID> {

    List<AdPlatformInsight> findByConnectionIdAndExternalAdIdAndDateStart(UUID connectionId, String externalAdId, LocalDate dateStart);

    @Query(value = """
        SELECT COALESCE(SUM(spend),0), COALESCE(SUM(impressions),0), COALESCE(SUM(clicks),0),
               COALESCE(SUM(conversions),0), COALESCE(SUM(revenue),0),
               COALESCE(SUM(reach),0)
        FROM ad_platform_insights
        WHERE connection_id=:connectionId AND date_start>=:since
        """, nativeQuery = true)
    Object[] aggregateByConnection(@Param("connectionId") UUID connectionId, @Param("since") LocalDate since);

    @Query(value = """
        SELECT a.external_campaign_id, a.campaign_name,
               COALESCE(SUM(i.spend),0) as total_spend, COALESCE(SUM(i.impressions),0) as total_impressions,
               COALESCE(SUM(i.clicks),0) as total_clicks, COALESCE(SUM(i.conversions),0) as total_conversions
        FROM ad_platform_insights i
        JOIN ad_platform_ads a ON a.connection_id=i.connection_id AND a.external_ad_id=i.external_ad_id
        WHERE i.connection_id=:connectionId AND i.date_start>=:since
        GROUP BY a.external_campaign_id, a.campaign_name
        ORDER BY total_spend DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topCampaigns(@Param("connectionId") UUID connectionId, @Param("since") LocalDate since, @Param("limit") int limit);

    @Query(value = """
        SELECT i.external_ad_id, a.ad_name,
               COALESCE(SUM(i.spend),0) as total_spend, COALESCE(SUM(i.impressions),0) as total_impressions,
               COALESCE(SUM(i.clicks),0) as total_clicks, COALESCE(SUM(i.conversions),0) as total_conversions,
               COALESCE(AVG(i.roas),0) as avg_roas
        FROM ad_platform_insights i
        JOIN ad_platform_ads a ON a.connection_id=i.connection_id AND a.external_ad_id=i.external_ad_id
        WHERE i.connection_id=:connectionId AND i.date_start>=:since
        GROUP BY i.external_ad_id, a.ad_name
        ORDER BY avg_roas DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topAds(@Param("connectionId") UUID connectionId, @Param("since") LocalDate since, @Param("limit") int limit);
}
