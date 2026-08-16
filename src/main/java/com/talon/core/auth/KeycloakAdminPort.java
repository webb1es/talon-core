package com.talon.core.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface KeycloakAdminPort {

    String createUser(String username, String email, String tempPassword, boolean requireUpdatePassword);

    Optional<String> findUserIdByUsername(String username);

    Map<String, Object> getUser(String keycloakId);

    void updateUser(String keycloakId, Map<String, Object> fields);

    void replaceUser(String keycloakId, Map<String, Object> representation);

    void deleteUser(String keycloakId);

    void resetPassword(String keycloakId, String newPassword);

    void assignRealmRole(String keycloakId, String roleName);
}
