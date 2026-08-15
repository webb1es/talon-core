package com.talon.core.repository;

import com.talon.core.domain.UserStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserStoreRepository extends JpaRepository<UserStore, UUID> {
    List<UserStore> findByUserId(UUID userId);

    List<UserStore> findByStoreId(UUID storeId);
}
