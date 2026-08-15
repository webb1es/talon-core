package com.talon.core.security;

import com.talon.core.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client for Keycloak's Admin REST API, authenticated via the
 * talon-core-admin service account (client_credentials). Never reachable
 * from a browser; called only for admin-triggered user management.
 */
@Service
public class KeycloakAdminClientService {

    /** The only realm roles this app manages — never touch anything else a
     * user might be assigned (default realm roles like offline_access). */
    private static final Set<String> TALON_ROLE_NAMES =
        Set.of("super_admin", "admin", "manager", "cashier");

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public KeycloakAdminClientService(OAuth2AuthorizedClientManager authorizedClientManager,
                                       @Value("${app.keycloak-admin-api-base}") String adminApiBase) {
        this.authorizedClientManager = authorizedClientManager;
        this.restClient = RestClient.builder().baseUrl(adminApiBase).build();
    }

    private String adminAccessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
            .withClientRegistrationId("keycloak-admin")
            .principal("talon-core-admin-service")
            .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("Could not obtain a Keycloak admin service-account token");
        }
        return client.getAccessToken().getTokenValue();
    }

    /**
     * Creates the Keycloak user with a temporary credential. When
     * requireUpdatePassword is true, an UPDATE_PASSWORD required action
     * forces the change on Keycloak's own page before login completes.
     * Returns the new Keycloak user id (its `sub`).
     */
    public String createUser(String username, String email, String tempPassword, boolean requireUpdatePassword) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("enabled", true);
        body.put("emailVerified", false);
        body.put("credentials", List.of(Map.of(
            "type", "password",
            "value", tempPassword,
            "temporary", true
        )));
        if (requireUpdatePassword) {
            body.put("requiredActions", List.of("UPDATE_PASSWORD"));
        }

        var response = createUserRequest(body);

        String location = response.getHeaders().getFirst("Location");
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return a Location header for the created user");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private ResponseEntity<Void> createUserRequest(Map<String, Object> body) {
        try {
            return restClient.post()
                .uri("/users")
                .header("Authorization", "Bearer " + adminAccessToken())
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use");
        }
    }

    /** Keycloak enforces username uniqueness natively — used to check for an existing user, not to create one. */
    @SuppressWarnings("unchecked")
    public Optional<String> findUserIdByUsername(String username) {
        List<Map<String, Object>> results = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/users").queryParam("username", username).queryParam("exact", true).build())
            .header("Authorization", "Bearer " + adminAccessToken())
            .retrieve()
            .body(List.class);
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) results.get(0).get("id"));
    }

    public void updateUser(String keycloakId, Map<String, Object> fields) {
        restClient.put()
            .uri("/users/{id}", keycloakId)
            .header("Authorization", "Bearer " + adminAccessToken())
            .body(fields)
            .retrieve()
            .toBodilessEntity();
    }

    public void deleteUser(String keycloakId) {
        restClient.delete()
            .uri("/users/{id}", keycloakId)
            .header("Authorization", "Bearer " + adminAccessToken())
            .retrieve()
            .toBodilessEntity();
    }

    /** Sets a new temporary credential — used for admin-triggered resets, forcing UPDATE_PASSWORD next login. */
    public void resetPassword(String keycloakId, String newPassword) {
        Map<String, Object> credential = Map.of(
            "type", "password",
            "value", newPassword,
            "temporary", true
        );
        restClient.put()
            .uri("/users/{id}/reset-password", keycloakId)
            .header("Authorization", "Bearer " + adminAccessToken())
            .body(credential)
            .retrieve()
            .toBodilessEntity();

        updateUser(keycloakId, Map.of("requiredActions", List.of("UPDATE_PASSWORD")));
    }

    /** Removes whichever Talon-managed realm role the user currently has and assigns the new one. */
    @SuppressWarnings("unchecked")
    public void assignRealmRole(String keycloakId, Role role) {
        String token = adminAccessToken();

        List<Map<String, Object>> currentMappings = restClient.get()
            .uri("/users/{id}/role-mappings/realm", keycloakId)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(List.class);

        if (currentMappings != null) {
            List<Map<String, Object>> toRemove = currentMappings.stream()
                .filter(m -> TALON_ROLE_NAMES.contains((String) m.get("name")))
                .filter(m -> !role.getValue().equals(m.get("name")))
                .toList();
            if (!toRemove.isEmpty()) {
                restClient.method(HttpMethod.DELETE)
                    .uri("/users/{id}/role-mappings/realm", keycloakId)
                    .header("Authorization", "Bearer " + token)
                    .body(toRemove)
                    .retrieve()
                    .toBodilessEntity();
            }
            boolean alreadyAssigned = currentMappings.stream()
                .anyMatch(m -> role.getValue().equals(m.get("name")));
            if (alreadyAssigned) {
                return;
            }
        }

        Map<String, Object> roleRepresentation = restClient.get()
            .uri("/roles/{roleName}", role.getValue())
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(Map.class);

        restClient.post()
            .uri("/users/{id}/role-mappings/realm", keycloakId)
            .header("Authorization", "Bearer " + token)
            .body(new ArrayList<>(List.of(roleRepresentation)))
            .retrieve()
            .toBodilessEntity();
    }
}
