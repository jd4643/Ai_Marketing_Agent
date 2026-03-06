package com.marketing.strategy.service;

import java.util.UUID;

public class BusinessProfileNotFoundException extends RuntimeException {

    private final UUID businessId;

    public BusinessProfileNotFoundException(UUID businessId) {
        super("Business profile not found: " + businessId);
        this.businessId = businessId;
    }

    public UUID getBusinessId() {
        return businessId;
    }
}
