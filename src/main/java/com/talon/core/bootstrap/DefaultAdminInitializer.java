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
 * Links the tadmin Keycloak identity (realm import) to a local User row on
 * startup — AdminUserController cannot create the first admin. No-op once
 * the row exists.
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    private static final String USERNAME = "tadmin";
    private static final String EMAIL = "admin@mytalon.co.zw";
    private static final String DISPLAY_NAME = "Admin Talon-App";

    private final UserRepository userRepository;
    private final KeycloakAdminClientService keycloakAdminClientService;

    public DefaultAdminInitializer(UserRepository userRepository,
                                    KeycloakAdminClientService keycloakAdminClientService) {
        this.userRepository = userRepository;
        this.keycloakAdminClientService = keycloakAdminClientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(USERNAME)) {
            return;
        }
        try {
            keycloakAdminClientService.findUserIdByUsername(USERNAME).ifPresent(this::createLocalRow);
        } catch (RuntimeException e) {
            // A boot-time convenience check must never block startup — Keycloak
            // being briefly unreachable here just means this stays a no-op.
            log.warn("Could not check Keycloak for {} at startup: {}", USERNAME, e.getMessage());
        }
    }

    private void createLocalRow(String keycloakId) {
        User user = new User();
        user.setKeycloakId(UUID.fromString(keycloakId));
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setDisplayName(DISPLAY_NAME);
        user.setRole(Role.SUPER_ADMIN);
        user.setActive(true);
        userRepository.save(user);
    }
}
