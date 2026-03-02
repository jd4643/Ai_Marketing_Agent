package com.marketing.analytics.repo;
import com.marketing.analytics.model.CampaignMetric;import java.time.Instant;import java.util.List;import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;
public interface CampaignMetricRepository extends JpaRepository<CampaignMetric,UUID>{
@Query(value="SELECT platform, COALESCE(SUM(spend),0), COALESCE(SUM(impressions),0), COALESCE(SUM(clicks),0), COALESCE(SUM(conversions),0), COALESCE(SUM(revenue),0), COALESCE(AVG(roas),0) FROM campaign_metrics WHERE business_id=:businessId AND recorded_at>=:since GROUP BY platform", nativeQuery=true)
List<Object[]> aggregate(@Param("businessId") UUID businessId,@Param("since") Instant since);
}
