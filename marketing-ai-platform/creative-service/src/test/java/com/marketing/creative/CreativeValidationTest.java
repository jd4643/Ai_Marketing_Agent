package com.marketing.creative;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketing.creative.service.CreativeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
class CreativeValidationTest {
  @Autowired MockMvc mvc;
  @MockBean CreativeService creativeService;

  @Test void badRequest() throws Exception {
    mvc.perform(post("/creative/generate").header("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
