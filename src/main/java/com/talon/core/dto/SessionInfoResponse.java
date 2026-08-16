package com.talon.core.dto;

import java.util.List;
import java.util.UUID;

/**
 * No forcePasswordChange field: Keycloak's UPDATE_PASSWORD required action
 * forces that on its own hosted page before login ever completes.
 */
public record SessionInfoResponse(
    UUID id,
    String username,
    String email,
    String displayName,
    String role,
    String avatarUrl,
    UUID storeId,
    List<StoreAssignmentDto> storeAssignments,
    boolean hasPin
) {
}
