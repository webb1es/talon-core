package com.talon.core.auth;

import java.util.Optional;
import java.util.UUID;

/** Persistence for the PIN hash. Hashing and verify stay in auth. */
public interface PinCredentialsPort {

    Optional<String> findHash(UUID userId);

    void replaceHash(UUID userId, String pinHash);

    void clearHash(UUID userId);
}
