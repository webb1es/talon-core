package com.talon.core.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountPort {

    Optional<LocalAccount> findById(UUID id);

    record LocalAccount(
        UUID id,
        String email,
        String displayName,
        boolean active,
        List<UUID> storeIds,
        UUID defaultStoreId
    ) {
    }
}
