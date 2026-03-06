package com.marketing.strategy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.strategy.api.BusinessProfileController;
import com.marketing.strategy.model.BusinessProfileResponse;
import com.marketing.strategy.model.CreateBusinessProfileRequest;
import com.marketing.strategy.model.CreateBusinessProfileResponse;
import com.marketing.strategy.model.UpdateBusinessProfileRequest;
import com.marketing.strategy.service.BusinessProfileNotFoundException;
import com.marketing.strategy.service.BusinessProfileService;
import com.marketing.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

@WebMvcTest(BusinessProfileController.class)
@Import(GlobalExceptionHandler.class)
class BusinessProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    BusinessProfileService service;

    // ---- Validation tests ----

    @Test
    void createMissingBusinessNameReturns400() throws Exception {
        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"industry":"jewelry"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createMissingIndustryReturns400() throws Exception {
        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Acme Jewelry"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createBlankBusinessNameReturns400() throws Exception {
        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"  ","industry":"jewelry"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMissingBusinessNameReturns400() throws Exception {
        mvc.perform(put("/strategy/business-profiles/" + UUID.randomUUID())
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"industry":"jewelry"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- Happy path tests ----

    @Test
    void createHappyPathReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.create(any(CreateBusinessProfileRequest.class)))
                .thenReturn(new CreateBusinessProfileResponse(id, now));

        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName":"Acme Jewelry",
                                  "industry":"jewelry",
                                  "product":"rings",
                                  "priceRange":"$50-$200",
                                  "location":"US",
                                  "targetAudience":"women 25-45",
                                  "websiteUrl":"https://acme.example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(id.toString()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createWithOnlyRequiredFieldsReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.create(any(CreateBusinessProfileRequest.class)))
                .thenReturn(new CreateBusinessProfileResponse(id, now));

        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Acme","industry":"jewelry"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(id.toString()));
    }

    @Test
    void getHappyPathReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.get(eq(id)))
                .thenReturn(new BusinessProfileResponse(id, "Acme Jewelry", "jewelry", "rings",
                        "$50-$200", "US", "women 25-45", "https://acme.example.com", now, now));

        mvc.perform(get("/strategy/business-profiles/" + id)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(id.toString()))
                .andExpect(jsonPath("$.businessName").value("Acme Jewelry"))
                .andExpect(jsonPath("$.industry").value("jewelry"))
                .andExpect(jsonPath("$.product").value("rings"))
                .andExpect(jsonPath("$.priceRange").value("$50-$200"))
                .andExpect(jsonPath("$.location").value("US"))
                .andExpect(jsonPath("$.targetAudience").value("women 25-45"))
                .andExpect(jsonPath("$.websiteUrl").value("https://acme.example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void updateHappyPathReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.update(eq(id), any(UpdateBusinessProfileRequest.class)))
                .thenReturn(new BusinessProfileResponse(id, "Acme Updated", "fashion", "bracelets",
                        "$100-$500", "EU", "women 30-50", "https://acme-new.example.com", now, now));

        mvc.perform(put("/strategy/business-profiles/" + id)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName":"Acme Updated",
                                  "industry":"fashion",
                                  "product":"bracelets",
                                  "priceRange":"$100-$500",
                                  "location":"EU",
                                  "targetAudience":"women 30-50",
                                  "websiteUrl":"https://acme-new.example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(id.toString()))
                .andExpect(jsonPath("$.businessName").value("Acme Updated"))
                .andExpect(jsonPath("$.industry").value("fashion"));
    }

    // ---- 404 tests ----

    @Test
    void getNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(eq(id))).thenThrow(new BusinessProfileNotFoundException(id));

        mvc.perform(get("/strategy/business-profiles/" + id)
                        .header("X-Request-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Business profile not found: " + id));
    }

    @Test
    void updateNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any(UpdateBusinessProfileRequest.class)))
                .thenThrow(new BusinessProfileNotFoundException(id));

        mvc.perform(put("/strategy/business-profiles/" + id)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Acme","industry":"jewelry"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ---- Request-Id propagation in error responses ----

    @Test
    void errorResponseIncludesRequestId() throws Exception {
        String reqId = UUID.randomUUID().toString();
        UUID id = UUID.randomUUID();
        when(service.get(eq(id))).thenThrow(new BusinessProfileNotFoundException(id));

        mvc.perform(get("/strategy/business-profiles/" + id)
                        .header("X-Request-Id", reqId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value(reqId));
    }

    @Test
    void createWithNullWebsiteUrlReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(service.create(any(CreateBusinessProfileRequest.class)))
                .thenReturn(new CreateBusinessProfileResponse(id, now));

        mvc.perform(post("/strategy/business-profiles")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Acme","industry":"jewelry","websiteUrl":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(id.toString()));
    }
}
