package com.talon.core.users.internal.repository;

import com.talon.core.users.internal.entity.UserStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserStoreRepository extends JpaRepository<UserStore, UUID> {
    List<UserStore> findByUserId(UUID userId);

    List<UserStore> findByStoreId(UUID storeId);

    boolean existsByStoreId(UUID storeId);
}
