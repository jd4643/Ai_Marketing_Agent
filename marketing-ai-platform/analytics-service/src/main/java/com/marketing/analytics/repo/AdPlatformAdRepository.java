package com.marketing.analytics.repo;

import com.marketing.analytics.model.AdPlatformAd;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdPlatformAdRepository extends JpaRepository<AdPlatformAd, UUID> {
    List<AdPlatformAd> findByConnectionId(UUID connectionId);
    Optional<AdPlatformAd> findByConnectionIdAndExternalAdId(UUID connectionId, String externalAdId);
    List<AdPlatformAd> findByBusinessId(UUID businessId);
}
