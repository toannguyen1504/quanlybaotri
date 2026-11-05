package com.example.quanlybaotri;

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
    void should_reject_unauthenticated_api_request() throws Exception {
        mvc.perform(get("/api/v1/__security-test")).andExpect(status().isUnauthorized());
    }

    @Test
    void should_require_internal_scope_for_internal_request() throws Exception {
        mvc.perform(get("/internal/v1/__security-test").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_allow_internal_scope_to_reach_internal_routing() throws Exception {
        mvc.perform(get("/internal/v1/__security-test").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_internal"))))
            .andExpect(status().isNotFound());
    }
}
