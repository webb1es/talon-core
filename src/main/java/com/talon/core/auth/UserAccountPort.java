package com.talon.core.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountPort {

    Optional<LocalAccount> findByKeycloakId(UUID keycloakId);

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
