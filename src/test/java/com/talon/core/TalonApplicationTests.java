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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every authenticated endpoint sits behind the session-based oauth2Login
 * chain, so requests are authenticated via oidcLogin() (a mock session
 * principal), not jwt() (a mock bearer token) — this app accepts no bearer
 * tokens at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TalonApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpointIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is a public endpoint"));
    }

    @Test
    void securedEndpointIsProtected() throws Exception {
        // oauth2Login redirects an unauthenticated browser to Keycloak rather
        // than returning a bare 401 — there's no bearer-token/resource-server
        // chain left to produce that response.
        mockMvc.perform(get("/api/secured"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/keycloak"));
    }

    @Test
    void securedEndpointAllowsAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/api/secured")
                .with(oidcLogin().idToken(token -> token.subject("test-user-id"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is a secured endpoint"))
                .andExpect(jsonPath("$.subject").value("test-user-id"));
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        // Without role
        mockMvc.perform(get("/api/admin")
                .with(oidcLogin().idToken(token -> token.subject("test-user-id"))))
                .andExpect(status().isForbidden());

        // With role
        mockMvc.perform(get("/api/admin")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is an admin-only endpoint"));
    }

    @Test
    void swaggerRequiresSuperAdminRole() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/swagger-ui/index.html")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_super_admin"))))
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
