package com.marketing.analytics.repo;

import com.marketing.analytics.model.AdPlatformConnection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdPlatformConnectionRepository extends JpaRepository<AdPlatformConnection, UUID> {
    List<AdPlatformConnection> findByBusinessIdAndPlatform(UUID businessId, String platform);
    List<AdPlatformConnection> findByBusinessId(UUID businessId);
    List<AdPlatformConnection> findByStatus(String status);
}
