package com.remotesupport.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Smoke test: the API boots against a real Postgres and reports itself healthy. */
class HealthEndpointTest extends IntegrationTest {

  @Test
  void healthEndpointReportsUp() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}"));
  }
}
