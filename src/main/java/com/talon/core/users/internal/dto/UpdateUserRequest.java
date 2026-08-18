package com.talon.core.users.internal.dto;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
    String displayName,
    String email,
    String phone,
    String group,
    List<UUID> storeIds,
    Boolean active
) {
}
