package com.marketing.strategy.model;

import java.time.Instant;
import java.util.UUID;

public record CreateBusinessProfileResponse(UUID businessId, Instant createdAt) {
}
