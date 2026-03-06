package com.marketing.strategy.api;

import com.marketing.strategy.model.BusinessProfileResponse;
import com.marketing.strategy.model.CreateBusinessProfileRequest;
import com.marketing.strategy.model.CreateBusinessProfileResponse;
import com.marketing.strategy.model.UpdateBusinessProfileRequest;
import com.marketing.strategy.service.BusinessProfileService;
import jakarta.validation.Valid;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/strategy/business-profiles")
public class BusinessProfileController {

    private static final Logger log = LoggerFactory.getLogger(BusinessProfileController.class);

    private final BusinessProfileService service;

    public BusinessProfileController(BusinessProfileService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBusinessProfileResponse create(
            @Valid @RequestBody CreateBusinessProfileRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        log.info("POST /strategy/business-profiles requestId={} businessName={}", requestId, req.businessName());
        return service.create(req);
    }

    @GetMapping("/{id}")
    public BusinessProfileResponse get(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        log.info("GET /strategy/business-profiles/{} requestId={}", id, requestId);
        return service.get(id);
    }

    @PutMapping("/{id}")
    public BusinessProfileResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessProfileRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        log.info("PUT /strategy/business-profiles/{} requestId={}", id, requestId);
        return service.update(id, req);
    }
}
