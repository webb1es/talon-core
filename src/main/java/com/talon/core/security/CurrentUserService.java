package com.talon.core.security;

import com.talon.core.domain.Role;
import com.talon.core.domain.User;
import com.talon.core.domain.UserStore;
import com.talon.core.repository.UserRepository;
import com.talon.core.repository.UserStoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the authenticated Keycloak subject to a local CurrentUserProfile
 * (role, store assignments). A Keycloak login with no matching local User row
 * is unauthorized, not auto-provisioned — local rows are created explicitly
 * by an admin's user-provisioning action, never implicitly on first login, so
 * an unrecognized identity can't silently pick up a default role/store scope.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;

    public CurrentUserService(UserRepository userRepository, UserStoreRepository userStoreRepository) {
        this.userRepository = userRepository;
        this.userStoreRepository = userStoreRepository;
    }

    public CurrentUserProfile resolve(OidcUser oidcUser) {
        UUID keycloakId = UUID.fromString(oidcUser.getSubject());
        User user = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "No local account for this Keycloak identity"));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account deactivated");
        }

        List<UserStore> assignments = userStoreRepository.findByUserId(user.getId());
        List<UUID> storeIds = assignments.stream().map(UserStore::getStoreId).toList();
        UUID defaultStoreId = assignments.stream()
            .filter(UserStore::isDefault)
            .map(UserStore::getStoreId)
            .findFirst()
            .orElse(null);

        return new CurrentUserProfile(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            liveRole(oidcUser),
            user.isActive(),
            storeIds,
            defaultStoreId
        );
    }

    /** The caller's own role comes from the token just authenticated with, not the local cache used for other users. */
    private String liveRole(OidcUser oidcUser) {
        return oidcUser.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .filter(role -> Arrays.stream(Role.values()).anyMatch(r -> r.getValue().equals(role)))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No Talon role assigned"));
    }
}
