package com.talon.core.users.internal.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
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
