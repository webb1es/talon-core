package com.talon.core.auth.internal.service;

import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.auth.UserAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the authenticated Keycloak subject to a local CurrentUserProfile.
 * A Keycloak login with no matching local account is unauthorized, not
 * auto-provisioned.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final Set<String> TALON_ROLES = Set.of("super_admin", "admin", "manager", "cashier");

    private final UserAccountPort userAccountPort;

    public CurrentUserProfile resolve(OidcUser oidcUser) {
        UUID subjectId = UUID.fromString(oidcUser.getSubject());
        UserAccountPort.LocalAccount account = userAccountPort.findById(subjectId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "No local account for this Keycloak identity"));

        if (!account.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account deactivated");
        }

        return new CurrentUserProfile(
            account.id(),
            account.email(),
            account.displayName(),
            liveRole(oidcUser),
            account.active(),
            account.storeIds(),
            account.defaultStoreId()
        );
    }

    /** The caller's own role comes from the token just authenticated with, not the local cache used for other users. */
    private String liveRole(OidcUser oidcUser) {
        return oidcUser.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .filter(TALON_ROLES::contains)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No Talon role assigned"));
    }
}
