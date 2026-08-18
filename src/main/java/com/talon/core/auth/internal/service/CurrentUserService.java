package com.talon.core.auth.internal.service;

import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.auth.UserAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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

    private static final Set<String> TALON_GROUPS = Set.of("super_admin", "admin", "manager", "cashier");

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
            liveGroup(oidcUser),
            account.active(),
            account.storeIds(),
            account.defaultStoreId()
        );
    }

    /** The caller's own group comes from the token just authenticated with, not the local cache used for other users. */
    private String liveGroup(OidcUser oidcUser) {
        List<String> groups = oidcUser.getClaimAsStringList("groups");
        return (groups == null ? List.<String>of() : groups).stream()
            .filter(TALON_GROUPS::contains)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No Talon group assigned"));
    }
}
