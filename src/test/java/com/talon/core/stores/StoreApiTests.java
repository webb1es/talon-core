package com.talon.core.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talon.core.stores.internal.entity.Store;
import com.talon.core.stores.internal.repository.StoreRepository;
import com.talon.core.users.internal.entity.Role;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreApiTests {

    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASHIER_KEYCLOAK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStoreRepository userStoreRepository;

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void seedUsers() {
        seedUser("admin-user", ADMIN_KEYCLOAK_ID, Role.ADMIN);
        seedUser("cashier-user", CASHIER_KEYCLOAK_ID, Role.CASHIER);
    }

    @Test
    void unauthenticatedListReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/stores"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void cashierCannotCreateStore() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                .with(cashierLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden Store\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateListGetPatchAndDeactivate() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/stores")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Harare Main\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.data.name").value("Harare Main"))
            .andExpect(jsonPath("$.data.currencyCode").value("USD"))
            .andExpect(jsonPath("$.data.timezone").value("Africa/Harare"))
            .andExpect(jsonPath("$.data.active").value(true))
            .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
            .get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/stores").with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].id").value(hasItem(id)));

        mockMvc.perform(get("/api/v1/stores/" + id).with(adminLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id));

        mockMvc.perform(patch("/api/v1/stores/" + id)
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taxRate\":0.15,\"active\":false,\"address\":\"1 Samora Machel\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false))
            .andExpect(jsonPath("$.data.address").value("1 Samora Machel"));
    }

    @Test
    void cashierListIsScopedToAssignedActiveStores() throws Exception {
        Store assigned = new Store();
        assigned.setName("Assigned");
        assigned = storeRepository.save(assigned);

        Store other = new Store();
        other.setName("Other");
        other = storeRepository.save(other);

        Store inactive = new Store();
        inactive.setName("Inactive Assigned");
        inactive.setActive(false);
        inactive = storeRepository.save(inactive);

        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();
        assignStore(cashier.getId(), assigned.getId());
        assignStore(cashier.getId(), inactive.getId());

        mockMvc.perform(get("/api/v1/stores").with(cashierLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].id").value(assigned.getId().toString()));

        mockMvc.perform(get("/api/v1/stores/" + other.getId()).with(cashierLogin()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/stores/" + inactive.getId()).with(cashierLogin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithMembershipReturns409() throws Exception {
        Store store = new Store();
        store.setName("Staffed");
        store = storeRepository.save(store);

        User cashier = userRepository.findByUsername("cashier-user").orElseThrow();
        assignStore(cashier.getId(), store.getId());

        mockMvc.perform(delete("/api/v1/stores/" + store.getId()).with(adminLogin()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deleteWithoutMembershipReturns204() throws Exception {
        Store store = new Store();
        store.setName("Empty");
        store = storeRepository.save(store);

        mockMvc.perform(delete("/api/v1/stores/" + store.getId()).with(adminLogin()))
            .andExpect(status().isNoContent());
    }

    @Test
    void createWithoutNameReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                .with(adminLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists());
    }

    private User seedUser(String username, UUID keycloakId, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setId(keycloakId);
            user.setUsername(username);
            user.setDisplayName(username);
            user.setRole(role);
            user.setActive(true);
            return userRepository.save(user);
        });
    }

    private void assignStore(UUID userId, UUID storeId) {
        UserStore assignment = new UserStore();
        assignment.setUserId(userId);
        assignment.setStoreId(storeId);
        assignment.setDefault(true);
        userStoreRepository.save(assignment);
    }

    private static RequestPostProcessor adminLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(ADMIN_KEYCLOAK_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }

    private static RequestPostProcessor cashierLogin() {
        return oidcLogin()
            .idToken(token -> token.subject(CASHIER_KEYCLOAK_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_cashier"));
    }
}
