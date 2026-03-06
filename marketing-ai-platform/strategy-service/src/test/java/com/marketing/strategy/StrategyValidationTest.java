package com.marketing.strategy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.strategy.service.BusinessProfileService;
import com.marketing.strategy.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class StrategyValidationTest {
  @Autowired MockMvc mvc;
  @MockBean StrategyService strategyService;
  @MockBean BusinessProfileService businessProfileService;

  @Test void badRequest() throws Exception {
    mvc.perform(post("/strategy/generate").header("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
