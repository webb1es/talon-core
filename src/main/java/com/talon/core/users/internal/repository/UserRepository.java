package com.talon.core.users.internal.repository;

import com.talon.core.users.internal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByKeycloakId(UUID keycloakId);

    Optional<User> findByUsername(String username);
}
