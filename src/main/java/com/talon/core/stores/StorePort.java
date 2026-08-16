package com.talon.core.stores;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorePort {

    boolean exists(UUID id);

    Optional<StoreSummary> findById(UUID id);

    List<StoreSummary> findAllById(Collection<UUID> ids);

    record StoreSummary(UUID id, String name) {
    }
}
