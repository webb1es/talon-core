package com.talon.core.users.internal.dto;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
    String displayName,
    String email,
    String phone,
    String role,
    UUID storeId,
    List<UUID> storeIds,
    Boolean active
) {
}
