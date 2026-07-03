package com.talon.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        mockMvc.perform(get("/api/secured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securedEndpointAllowsAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/api/secured")
                .with(jwt().jwt(builder -> builder.subject("test-user-id"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is a secured endpoint"))
                .andExpect(jsonPath("$.subject").value("test-user-id"));
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        // Without role
        mockMvc.perform(get("/api/admin")
                .with(jwt().jwt(builder -> builder.subject("test-user-id"))))
                .andExpect(status().isForbidden());

        // With role
        mockMvc.perform(get("/api/admin")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is an admin-only endpoint"));
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
