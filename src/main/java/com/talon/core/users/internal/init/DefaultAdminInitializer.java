package com.talon.core.users.internal.init;

import com.talon.core.users.internal.entity.Group;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.repository.UserRepository;
import com.talon.core.auth.KeycloakAdminPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * First super_admin cannot be created through UserController. Every boot
 * checks that tadmin exists in Keycloak and in the local users row, creating
 * whichever side is missing. User.id is the Keycloak subject, so if the local
 * row's id ever drifts from Keycloak's (e.g. a realm reset), it's recreated
 * rather than repointed — JPA can't reassign an existing row's id. No-op when
 * both already match, or if Keycloak is unreachable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final String USERNAME = "tadmin";
    private static final String EMAIL = "admin@mytalon.co.zw";
    private static final String DISPLAY_NAME = "Admin Talon-App";
    /** First sign-in only — createUser sets UPDATE_PASSWORD so Keycloak discards this. */
    private static final String BOOTSTRAP_PASSWORD = "ChangeMeNow!";

    private final UserRepository userRepository;
    private final KeycloakAdminPort keycloakAdminPort;

    @Override
    public void run(ApplicationArguments args) {
        String createdKeycloakId = null;
        try {
            User local = userRepository.findByUsername(USERNAME).orElse(null);
            String keycloakId = keycloakAdminPort.findUserIdByUsername(USERNAME).orElse(null);

            if (keycloakId == null) {
                keycloakId = keycloakAdminPort.createUser(USERNAME, EMAIL, BOOTSTRAP_PASSWORD, true);
                createdKeycloakId = keycloakId;
                keycloakAdminPort.assignGroup(keycloakId, Group.SUPER_ADMIN.getValue());
            }

            UUID linkedId = UUID.fromString(keycloakId);
            if (local == null) {
                createLocalRow(linkedId);
                return;
            }
            if (!linkedId.equals(local.getId())) {
                log.warn("Local {} row id {} does not match Keycloak id {} — recreating local row",
                    USERNAME, local.getId(), linkedId);
                userRepository.delete(local);
                createLocalRow(linkedId);
            }
        } catch (RuntimeException e) {
            if (createdKeycloakId != null) {
                try {
                    keycloakAdminPort.deleteUser(createdKeycloakId);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local bootstrap failed", createdKeycloakId, cleanup);
                }
            }
            log.warn("Could not reconcile {} at startup: {}", USERNAME, e.getMessage());
        }
    }

    private void createLocalRow(UUID keycloakId) {
        User user = new User();
        user.setId(keycloakId);
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setDisplayName(DISPLAY_NAME);
        user.setGroup(Group.SUPER_ADMIN);
        user.setActive(true);
        userRepository.save(user);
    }
}
