package com.example.quanlybaotri.ai;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void should_reject_unauthenticated_ai_request() throws Exception {
        mvc.perform(get("/api/v1/ai/__security-test")).andExpect(status().isUnauthorized());
    }

    @Test
    void should_allow_supported_role_to_reach_ai_routing() throws Exception {
        mvc.perform(get("/api/v1/ai/__security-test").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_deny_internal_paths() throws Exception {
        mvc.perform(get("/internal/v1/__security-test").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_internal"))))
            .andExpect(status().isForbidden());
    }
}
