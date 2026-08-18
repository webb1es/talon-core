package com.talon.core.auth;

import java.util.List;
import java.util.UUID;

public record CurrentUserProfile(
    UUID id,
    String email,
    String name,
    String group,
    boolean active,
    List<UUID> storeIds,
    UUID defaultStoreId
) {
}
