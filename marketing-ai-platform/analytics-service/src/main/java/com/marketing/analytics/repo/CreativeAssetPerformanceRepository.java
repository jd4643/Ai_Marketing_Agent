package com.marketing.analytics.repo;
import com.marketing.analytics.model.CreativeAssetPerformance;import java.time.Instant;import java.util.List;import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;
public interface CreativeAssetPerformanceRepository extends JpaRepository<CreativeAssetPerformance,UUID>{

@Query(value="SELECT creative_asset_id, platform, COALESCE(SUM(impressions),0), COALESCE(SUM(clicks),0), COALESCE(SUM(conversions),0), COALESCE(SUM(spend),0), COALESCE(SUM(revenue),0), COALESCE(AVG(roas),0) FROM creative_asset_performance WHERE business_id=:businessId AND recorded_at>=:since GROUP BY creative_asset_id, platform", nativeQuery=true)
List<Object[]> aggregateByAsset(@Param("businessId") UUID businessId,@Param("since") Instant since);

@Query(value="""
SELECT cap.creative_asset_id, cap.platform,
       COALESCE(SUM(cap.impressions),0) as total_impressions,
       COALESCE(SUM(cap.clicks),0) as total_clicks,
       COALESCE(SUM(cap.conversions),0) as total_conversions,
       COALESCE(SUM(cap.spend),0) as total_spend,
       COALESCE(SUM(cap.revenue),0) as total_revenue,
       COALESCE(AVG(cap.roas),0) as avg_roas,
       ca.metadata_json, ca.asset_type, ca.prompt_text
FROM creative_asset_performance cap
JOIN creative_assets ca ON ca.id = cap.creative_asset_id
WHERE cap.business_id=:businessId AND cap.recorded_at>=:since
GROUP BY cap.creative_asset_id, cap.platform, ca.metadata_json, ca.asset_type, ca.prompt_text
HAVING COALESCE(SUM(cap.impressions),0) >= :minImpressions
ORDER BY avg_roas DESC
LIMIT :limit
""", nativeQuery=true)
List<Object[]> findWinners(@Param("businessId") UUID businessId,@Param("since") Instant since,@Param("minImpressions") long minImpressions,@Param("limit") int limit);

}
