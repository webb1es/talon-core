package com.talon.core.users;

import com.talon.core.stores.internal.entity.Store;
import com.talon.core.stores.internal.repository.StoreRepository;
import com.talon.core.users.internal.entity.Group;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.entity.UserStore;
import com.talon.core.users.internal.repository.UserRepository;
import com.talon.core.users.internal.repository.UserStoreRepository;
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
    private static final UUID SUPER_ADMIN_KEYCLOAK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MANAGER_KEYCLOAK_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStoreRepository userStoreRepository;

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void seedUsers() {
        seedUser("admin-user", ADMIN_KEYCLOAK_ID, Group.ADMIN, true);
        seedUser("cashier-user", CASHIER_KEYCLOAK_ID, Group.CASHIER, true);
    }

    @Test
    void unauthenticatedMeReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void meReturnsTheCallerWithoutTopLevelStoreId() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin-user"))
            .andExpect(jsonPath("$.data.displayName").value("admin-user"))
            .andExpect(jsonPath("$.data.group").value("admin"))
            .andExpect(jsonPath("$.data.hasPin").value(false))
            .andExpect(jsonPath("$.data.storeId").doesNotExist())
            .andExpect(jsonPath("$.data.storeAssignments").isArray());
    }

    @Test
    void legacySessionInfoPathIsGone() throws Exception {
        mockMvc.perform(get("/api/profile/session-info").with(adminLogin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void patchMeUpdatesDisplayName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Floor Admin\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("Floor Admin"));
    }

    @Test
    void adminCanListAndGetOtherUsers() throws Exception {
        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();

        mockMvc.perform(get("/api/v1/users").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].username").value("cashier-user"))
            .andExpect(jsonPath("$.data.items[0].storeId").doesNotExist());

        mockMvc.perform(get("/api/v1/users/" + cashier.getId()).with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("cashier-user"));
    }

    @Test
    void adminCanPatchUserDisplayName() throws Exception {
        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();

        mockMvc.perform(patch("/api/v1/users/" + cashier.getId())
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Register One\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("Register One"));
    }

    @Test
    void adminCannotReadOverview() throws Exception {
        mockMvc.perform(get("/api/v1/users/overview").with(adminLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void superAdminOverviewCountsStaffAndAttention() throws Exception {
        seedUser("super-admin", SUPER_ADMIN_KEYCLOAK_ID, Group.SUPER_ADMIN, true);
        seedUser("manager-user", MANAGER_KEYCLOAK_ID, Group.MANAGER, false);

        Store store = new Store();
        store.setName("Harare Main");
        store = storeRepository.save(store);
        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();
        UserStore assignment = new UserStore();
        assignment.setUserId(cashier.getId());
        assignment.setStoreId(store.getId());
        assignment.setDefault(true);
        userStoreRepository.save(assignment);

        mockMvc.perform(get("/api/v1/users/overview").with(superAdminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalUsers").value(3))
            .andExpect(jsonPath("$.data.activeUsers").value(2))
            .andExpect(jsonPath("$.data.inactiveUsers").value(1))
            .andExpect(jsonPath("$.data.unassignedUsers").value(2))
            .andExpect(jsonPath("$.data.groups[0].group").value("admin"))
            .andExpect(jsonPath("$.data.groups[0].count").value(1))
            .andExpect(jsonPath("$.data.groups[1].group").value("manager"))
            .andExpect(jsonPath("$.data.groups[1].count").value(1))
            .andExpect(jsonPath("$.data.groups[1].activeCount").value(0))
            .andExpect(jsonPath("$.data.groups[2].group").value("cashier"))
            .andExpect(jsonPath("$.data.groups[2].count").value(1))
            .andExpect(jsonPath("$.data.attention", hasSize(2)));
    }

    private User seedUser(String username, UUID keycloakId, Group group, boolean active) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setId(keycloakId);
            user.setUsername(username);
            user.setDisplayName(username);
            user.setGroup(group);
            user.setActive(active);
            return userRepository.save(user);
        });
    }

    private static RequestPostProcessor adminLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(ADMIN_KEYCLOAK_ID.toString())
                .claim("groups", java.util.List.of("admin")))
            .authorities(new SimpleGrantedAuthority("ROLE_manage_users"));
    }

    private static RequestPostProcessor superAdminLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(SUPER_ADMIN_KEYCLOAK_ID.toString())
                .claim("groups", java.util.List.of("super_admin")))
            .authorities(new SimpleGrantedAuthority("ROLE_view_users_overview"));
    }
}
