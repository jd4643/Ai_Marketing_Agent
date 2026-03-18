package com.marketing.analytics.repo;

import com.marketing.analytics.model.BusinessProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {}
