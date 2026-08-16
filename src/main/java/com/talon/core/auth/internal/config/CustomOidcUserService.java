package com.talon.core.auth.internal.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps Keycloak's realm_access/resource_access claims into Spring ROLE_*
 * authorities, reading from the merged ID-token + userinfo claims
 * OidcUserService assembles (there's no JWT principal in a BFF — only oauth2Login).
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Set<GrantedAuthority> authorities = extractAuthorities(oidcUser.getClaims());
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    @SuppressWarnings("unchecked")
    private Set<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        Stream<String> realmRoles = Stream.empty();
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap && realmAccessMap.containsKey("roles")) {
            Collection<String> roles = (Collection<String>) realmAccessMap.get("roles");
            realmRoles = roles.stream();
        }

        Stream<String> clientRoles = Stream.empty();
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
            clientRoles = resourceAccessMap.values().stream()
                .filter(Map.class::isInstance)
                .map(val -> (Map<String, Object>) val)
                .filter(map -> map.containsKey("roles"))
                .flatMap(map -> {
                    Collection<String> roles = (Collection<String>) map.get("roles");
                    return roles.stream();
                });
        }

        return Stream.concat(realmRoles, clientRoles)
            .distinct()
            .map(role -> "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
    }
}
