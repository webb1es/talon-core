package com.talon.core.users.internal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String phone,
    String displayName,
    String role,
    boolean active,
    String avatarUrl,
    Instant createdAt,
    List<StoreAssignmentDto> storeAssignments
) {
}
