package com.marketing.strategy.service;

import com.marketing.strategy.model.BusinessProfileEntity;
import com.marketing.strategy.model.BusinessProfileRepository;
import com.marketing.strategy.model.BusinessProfileResponse;
import com.marketing.strategy.model.CreateBusinessProfileRequest;
import com.marketing.strategy.model.CreateBusinessProfileResponse;
import com.marketing.strategy.model.UpdateBusinessProfileRequest;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BusinessProfileService {

    private static final Logger log = LoggerFactory.getLogger(BusinessProfileService.class);

    private final BusinessProfileRepository repository;

    public BusinessProfileService(BusinessProfileRepository repository) {
        this.repository = repository;
    }

    public CreateBusinessProfileResponse create(CreateBusinessProfileRequest req) {
        BusinessProfileEntity entity = new BusinessProfileEntity();
        entity.setId(UUID.randomUUID());
        entity.setBusinessName(req.businessName());
        entity.setIndustry(req.industry());
        entity.setProduct(req.product());
        entity.setPriceRange(req.priceRange());
        entity.setLocation(req.location());
        entity.setTargetAudience(req.targetAudience());
        entity.setWebsiteUrl(req.websiteUrl());

        entity = repository.save(entity);
        log.info("Created business profile id={} name={}", entity.getId(), entity.getBusinessName());
        return new CreateBusinessProfileResponse(entity.getId(), entity.getCreatedAt());
    }

    public BusinessProfileResponse get(UUID id) {
        BusinessProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessProfileNotFoundException(id));
        return toResponse(entity);
    }

    public BusinessProfileResponse update(UUID id, UpdateBusinessProfileRequest req) {
        BusinessProfileEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessProfileNotFoundException(id));

        entity.setBusinessName(req.businessName());
        entity.setIndustry(req.industry());
        entity.setProduct(req.product());
        entity.setPriceRange(req.priceRange());
        entity.setLocation(req.location());
        entity.setTargetAudience(req.targetAudience());
        entity.setWebsiteUrl(req.websiteUrl());

        entity = repository.save(entity);
        log.info("Updated business profile id={}", entity.getId());
        return toResponse(entity);
    }

    private BusinessProfileResponse toResponse(BusinessProfileEntity e) {
        return new BusinessProfileResponse(
                e.getId(),
                e.getBusinessName(),
                e.getIndustry(),
                e.getProduct(),
                e.getPriceRange(),
                e.getLocation(),
                e.getTargetAudience(),
                e.getWebsiteUrl(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
