package com.talon.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated endpoints sit behind oauth2Login, so tests use oidcLogin()
 * (session principal), not jwt() — this app accepts no bearer tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TalonApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedApiReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void swaggerRequiresSuperAdminRole() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_manage_users"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/swagger-ui/index.html")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_manage_system_settings"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void readyzEndpointIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthzEndpointIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
