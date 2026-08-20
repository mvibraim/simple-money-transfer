package com.example.simple_money_transfers.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiKeyAuthIT extends AbstractIntegrationTest {

  private static final String VALID_KEY = "test-api-key-that-is-at-least-32-characters-long";

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointRequiresNoKey() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void protectedEndpointRejectsMissingKey() throws Exception {
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void protectedEndpointRejectsWrongKey() throws Exception {
    mockMvc
        .perform(
            get("/actuator/info")
                .header(ApiKeyAuthFilter.HEADER_NAME, "wrong-key-wrong-key-wrong-key-wrong-key"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpointAcceptsValidKey() throws Exception {
    mockMvc
        .perform(get("/actuator/info").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
        .andExpect(status().isOk());
  }
}
