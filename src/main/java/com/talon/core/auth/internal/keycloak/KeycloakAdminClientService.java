package com.talon.core.auth.internal.keycloak;

import com.talon.core.auth.KeycloakAdminPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Client for Keycloak's Admin REST API, authenticated via the
 * talon-core-admin service account (client_credentials). Never reachable
 * from a browser; called only for admin-triggered user management.
 */
@Service
public class KeycloakAdminClientService implements KeycloakAdminPort {

    /** The only realm roles this app manages — never touch anything else a
     * user might be assigned (default realm roles like offline_access). */
    private static final Set<String> TALON_ROLE_NAMES =
        Set.of("super_admin", "admin", "manager", "cashier");

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public KeycloakAdminClientService(OAuth2AuthorizedClientManager authorizedClientManager,
                                       @Value("${app.keycloak-admin-api-base}") String adminApiBase) {
        this.authorizedClientManager = authorizedClientManager;
        this.restClient = RestClient.builder()
            .baseUrl(adminApiBase)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().setBearerAuth(adminAccessToken());
                return execution.execute(request, body);
            })
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                int code = response.getStatusCode().value();
                if (code == 409) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use");
                }
                if (code == 404) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Keycloak user not found");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin API error");
            })
            .build();
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

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin API unreachable", e);
        }
    }

    @Override
    public String createUser(String username, String email, String tempPassword, boolean requireUpdatePassword) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("enabled", true);
        body.put("emailVerified", true);
        body.put("credentials", List.of(Map.of(
            "type", "password",
            "value", tempPassword,
            "temporary", true
        )));
        if (requireUpdatePassword) {
            body.put("requiredActions", List.of("UPDATE_PASSWORD"));
        }

        ResponseEntity<Void> response = call(() -> restClient.post()
            .uri("/users")
            .body(body)
            .retrieve()
            .toBodilessEntity());

        String location = response.getHeaders().getFirst("Location");
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return a Location header for the created user");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> findUserIdByUsername(String username) {
        List<Map<String, Object>> results = call(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/users").queryParam("username", username).queryParam("exact", true).build())
            .retrieve()
            .body(List.class));
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) results.get(0).get("id"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUser(String keycloakId) {
        Map<String, Object> user = call(() -> restClient.get()
            .uri("/users/{id}", keycloakId)
            .retrieve()
            .body(Map.class));
        if (user == null) {
            throw new IllegalStateException("Keycloak returned an empty user representation");
        }
        return user;
    }

    @Override
    public void updateUser(String keycloakId, Map<String, Object> fields) {
        Map<String, Object> existing = getUser(keycloakId);
        existing.remove("access");
        existing.putAll(fields);
        putUser(keycloakId, existing);
    }

    @Override
    public void replaceUser(String keycloakId, Map<String, Object> representation) {
        Map<String, Object> body = new HashMap<>(representation);
        body.remove("access");
        putUser(keycloakId, body);
    }

    private void putUser(String keycloakId, Map<String, Object> body) {
        call(() -> {
            restClient.put()
                .uri("/users/{id}", keycloakId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void deleteUser(String keycloakId) {
        try {
            call(() -> {
                restClient.delete()
                    .uri("/users/{id}", keycloakId)
                    .retrieve()
                    .toBodilessEntity();
                return null;
            });
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    @Override
    public void resetPassword(String keycloakId, String newPassword) {
        Map<String, Object> credential = Map.of(
            "type", "password",
            "value", newPassword,
            "temporary", true
        );
        call(() -> {
            restClient.put()
                .uri("/users/{id}/reset-password", keycloakId)
                .body(credential)
                .retrieve()
                .toBodilessEntity();
            return null;
        });

        updateUser(keycloakId, Map.of("requiredActions", List.of("UPDATE_PASSWORD")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void assignRealmRole(String keycloakId, String roleName) {
        List<Map<String, Object>> currentMappings = call(() -> restClient.get()
            .uri("/users/{id}/role-mappings/realm", keycloakId)
            .retrieve()
            .body(List.class));

        if (currentMappings != null) {
            List<Map<String, Object>> toRemove = currentMappings.stream()
                .filter(m -> TALON_ROLE_NAMES.contains((String) m.get("name")))
                .filter(m -> !roleName.equals(m.get("name")))
                .toList();
            if (!toRemove.isEmpty()) {
                call(() -> {
                    restClient.method(HttpMethod.DELETE)
                        .uri("/users/{id}/role-mappings/realm", keycloakId)
                        .body(toRemove)
                        .retrieve()
                        .toBodilessEntity();
                    return null;
                });
            }
            boolean alreadyAssigned = currentMappings.stream()
                .anyMatch(m -> roleName.equals(m.get("name")));
            if (alreadyAssigned) {
                return;
            }
        }

        Map<String, Object> roleRepresentation = call(() -> restClient.get()
            .uri("/roles/{roleName}", roleName)
            .retrieve()
            .body(Map.class));

        call(() -> {
            restClient.post()
                .uri("/users/{id}/role-mappings/realm", keycloakId)
                .body(new ArrayList<>(List.of(roleRepresentation)))
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }
}
