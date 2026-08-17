package com.talon.core.auth;

import com.talon.core.users.internal.entity.Role;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTests {

    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("admin-user").orElseGet(() -> {
            User user = new User();
            user.setId(ADMIN_KEYCLOAK_ID);
            user.setUsername("admin-user");
            user.setDisplayName("admin-user");
            user.setRole(Role.ADMIN);
            user.setActive(true);
            return userRepository.save(user);
        });
    }

    @Test
    void unauthenticatedPinEndpointsReturn401Json() throws Exception {
        mockMvc.perform(put("/api/v1/auth/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1234\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/auth/pin/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1234\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void putPinThenVerifySucceeds() throws Exception {
        mockMvc.perform(put("/api/v1/auth/pin")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"2468\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/pin/verify")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"2468\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true));

        mockMvc.perform(post("/api/v1/auth/pin/verify")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"0000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void setPinIsReflectedOnMe() throws Exception {
        mockMvc.perform(put("/api/v1/auth/pin")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1357\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasPin").value(true));
    }

    @Test
    void deletePinClearsCredential() throws Exception {
        mockMvc.perform(put("/api/v1/auth/pin")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"2468\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/auth/pin").with(adminLogin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/pin/verify")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"2468\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(false));

        mockMvc.perform(get("/api/v1/users/me").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasPin").value(false));
    }

    private static RequestPostProcessor adminLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(ADMIN_KEYCLOAK_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
