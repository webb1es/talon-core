package com.talon.core.users;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsersApiTests {

    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASHIER_KEYCLOAK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedUsers() {
        seedUser("admin-user", ADMIN_KEYCLOAK_ID, Role.ADMIN);
        seedUser("cashier-user", CASHIER_KEYCLOAK_ID, Role.CASHIER);
    }

    @Test
    void unauthenticatedMeReturns401Json() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void meAndSessionInfoAliasReturnTheCaller() throws Exception {
        mockMvc.perform(get("/api/me").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin-user"))
            .andExpect(jsonPath("$.displayName").value("admin-user"))
            .andExpect(jsonPath("$.role").value("admin"))
            .andExpect(jsonPath("$.hasPin").value(false));

        mockMvc.perform(get("/api/profile/session-info").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin-user"));
    }

    @Test
    void patchMeUpdatesDisplayName() throws Exception {
        mockMvc.perform(patch("/api/me")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Floor Admin\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Floor Admin"));
    }

    @Test
    void adminCanListAndGetOtherUsers() throws Exception {
        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();

        mockMvc.perform(get("/api/users").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].username").value("cashier-user"));

        mockMvc.perform(get("/api/users/" + cashier.getId()).with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("cashier-user"));
    }

    @Test
    void adminCanPatchUserDisplayName() throws Exception {
        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();

        mockMvc.perform(patch("/api/users/" + cashier.getId())
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Register One\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Register One"));
    }

    private User seedUser(String username, UUID keycloakId, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setUsername(username);
            user.setDisplayName(username);
            user.setRole(role);
            user.setActive(true);
            return userRepository.save(user);
        });
    }

    private static RequestPostProcessor adminLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(ADMIN_KEYCLOAK_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
