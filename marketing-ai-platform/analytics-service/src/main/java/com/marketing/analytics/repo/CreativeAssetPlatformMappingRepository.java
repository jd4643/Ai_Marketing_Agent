package com.marketing.analytics.repo;

import com.marketing.analytics.model.CreativeAssetPlatformMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativeAssetPlatformMappingRepository extends JpaRepository<CreativeAssetPlatformMapping, UUID> {
    List<CreativeAssetPlatformMapping> findByConnectionId(UUID connectionId);
    List<CreativeAssetPlatformMapping> findByBusinessIdAndPlatform(UUID businessId, String platform);
    List<CreativeAssetPlatformMapping> findByExternalAdId(String externalAdId);
    long countByConnectionId(UUID connectionId);
}
