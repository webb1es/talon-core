package com.talon.core.bootstrap;

import com.talon.core.domain.Role;
import com.talon.core.domain.User;
import com.talon.core.repository.UserRepository;
import com.talon.core.security.KeycloakAdminClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * First super_admin cannot be created through AdminUserController. Every boot
 * checks that tadmin exists in Keycloak and in the local users row, creating
 * whichever side is missing and aligning keycloakId. No-op when both already
 * match, or if Keycloak is unreachable.
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    private static final String USERNAME = "tadmin";
    private static final String EMAIL = "admin@mytalon.co.zw";
    private static final String DISPLAY_NAME = "Admin Talon-App";
    /** First sign-in only — createUser sets UPDATE_PASSWORD so Keycloak discards this. */
    private static final String BOOTSTRAP_PASSWORD = "ChangeMeNow!";

    private final UserRepository userRepository;
    private final KeycloakAdminClientService keycloakAdminClientService;

    public DefaultAdminInitializer(UserRepository userRepository,
                                    KeycloakAdminClientService keycloakAdminClientService) {
        this.userRepository = userRepository;
        this.keycloakAdminClientService = keycloakAdminClientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String createdKeycloakId = null;
        try {
            User local = userRepository.findByUsername(USERNAME).orElse(null);
            String keycloakId = keycloakAdminClientService.findUserIdByUsername(USERNAME).orElse(null);

            if (keycloakId == null) {
                keycloakId = keycloakAdminClientService.createUser(USERNAME, EMAIL, BOOTSTRAP_PASSWORD, true);
                createdKeycloakId = keycloakId;
                keycloakAdminClientService.assignRealmRole(keycloakId, Role.SUPER_ADMIN);
            }

            UUID linkedId = UUID.fromString(keycloakId);
            if (local == null) {
                createLocalRow(linkedId);
                return;
            }
            if (!linkedId.equals(local.getKeycloakId())) {
                local.setKeycloakId(linkedId);
                userRepository.save(local);
            }
        } catch (RuntimeException e) {
            if (createdKeycloakId != null) {
                try {
                    keycloakAdminClientService.deleteUser(createdKeycloakId);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local bootstrap failed", createdKeycloakId, cleanup);
                }
            }
            // A boot-time convenience check must never block startup — Keycloak
            // being briefly unreachable here just means this stays a no-op.
            log.warn("Could not reconcile {} at startup: {}", USERNAME, e.getMessage());
        }
    }

    private void createLocalRow(UUID keycloakId) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setDisplayName(DISPLAY_NAME);
        user.setRole(Role.SUPER_ADMIN);
        user.setActive(true);
        userRepository.save(user);
    }
}
