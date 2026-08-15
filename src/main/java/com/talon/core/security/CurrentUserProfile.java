package com.talon.core.security;

import java.util.List;
import java.util.UUID;

/** The authenticated user's role and store scope for the current request. */
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
