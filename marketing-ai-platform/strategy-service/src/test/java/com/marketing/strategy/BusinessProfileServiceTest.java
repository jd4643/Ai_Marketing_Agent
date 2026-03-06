package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.marketing.strategy.model.BusinessProfileEntity;
import com.marketing.strategy.model.BusinessProfileRepository;
import com.marketing.strategy.model.BusinessProfileResponse;
import com.marketing.strategy.model.CreateBusinessProfileRequest;
import com.marketing.strategy.model.CreateBusinessProfileResponse;
import com.marketing.strategy.model.UpdateBusinessProfileRequest;
import com.marketing.strategy.service.BusinessProfileNotFoundException;
import com.marketing.strategy.service.BusinessProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {

    @Mock
    BusinessProfileRepository repository;

    @InjectMocks
    BusinessProfileService service;

    @Test
    void createReturnsIdAndTimestamp() {
        BusinessProfileEntity saved = entity(UUID.randomUUID());
        when(repository.save(any(BusinessProfileEntity.class))).thenReturn(saved);

        CreateBusinessProfileResponse resp = service.create(
                new CreateBusinessProfileRequest("Acme", "jewelry", "rings", "$50-$200", "US", "women 25-45", "https://acme.example.com"));

        assertEquals(saved.getId(), resp.businessId());
        assertNotNull(resp.createdAt());
    }

    @Test
    void getReturnsFullResponse() {
        UUID id = UUID.randomUUID();
        BusinessProfileEntity e = entity(id);
        when(repository.findById(id)).thenReturn(Optional.of(e));

        BusinessProfileResponse resp = service.get(id);

        assertEquals(id, resp.businessId());
        assertEquals("Acme", resp.businessName());
        assertEquals("jewelry", resp.industry());
        assertEquals("rings", resp.product());
        assertEquals("$50-$200", resp.priceRange());
        assertEquals("US", resp.location());
        assertEquals("women 25-45", resp.targetAudience());
        assertEquals("https://acme.example.com", resp.websiteUrl());
        assertNotNull(resp.createdAt());
        assertNotNull(resp.updatedAt());
    }

    @Test
    void getNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        BusinessProfileNotFoundException ex = assertThrows(
                BusinessProfileNotFoundException.class, () -> service.get(id));
        assertEquals(id, ex.getBusinessId());
    }

    @Test
    void updateReturnsUpdatedFields() {
        UUID id = UUID.randomUUID();
        BusinessProfileEntity existing = entity(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(BusinessProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessProfileResponse resp = service.update(id,
                new UpdateBusinessProfileRequest("Acme Updated", "fashion", "bracelets", "$100-$500", "EU", "women 30-50", null));

        assertEquals("Acme Updated", resp.businessName());
        assertEquals("fashion", resp.industry());
        assertEquals("bracelets", resp.product());
    }

    @Test
    void updateNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(BusinessProfileNotFoundException.class,
                () -> service.update(id, new UpdateBusinessProfileRequest("X", "Y", null, null, null, null, null)));
    }

    private BusinessProfileEntity entity(UUID id) {
        BusinessProfileEntity e = new BusinessProfileEntity();
        e.setId(id);
        e.setBusinessName("Acme");
        e.setIndustry("jewelry");
        e.setProduct("rings");
        e.setPriceRange("$50-$200");
        e.setLocation("US");
        e.setTargetAudience("women 25-45");
        e.setWebsiteUrl("https://acme.example.com");
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}
