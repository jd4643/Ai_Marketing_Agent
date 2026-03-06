package com.marketing.strategy.model;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfileResponse(
        UUID businessId,
        String businessName,
        String industry,
        String product,
        String priceRange,
        String location,
        String targetAudience,
        String websiteUrl,
        Instant createdAt,
        Instant updatedAt) {
}
