package com.talon.core.security;

import java.util.List;
import java.util.UUID;

public record CurrentUserProfile(
    UUID id,
    String email,
    String name,
    String role,
    boolean active,
    List<UUID> storeIds,
    UUID defaultStoreId
) {
}
