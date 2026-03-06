package com.marketing.strategy.model;

import jakarta.validation.constraints.NotBlank;

public record CreateBusinessProfileRequest(
        @NotBlank(message = "businessName is required") String businessName,
        @NotBlank(message = "industry is required") String industry,
        String product,
        String priceRange,
        String location,
        String targetAudience,
        String websiteUrl) {
}
