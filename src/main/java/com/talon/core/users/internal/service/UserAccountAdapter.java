package com.talon.core.users.internal.service;

import com.talon.core.users.internal.entity.UserStore;
import com.talon.core.users.internal.repository.UserRepository;
import com.talon.core.users.internal.repository.UserStoreRepository;
import com.talon.core.auth.UserAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccountAdapter implements UserAccountPort {

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalAccount> findById(UUID id) {
        return userRepository.findById(id).map(user -> {
            List<UserStore> assignments = userStoreRepository.findByUserId(user.getId());
            List<UUID> storeIds = assignments.stream().map(UserStore::getStoreId).toList();
            UUID defaultStoreId = assignments.stream()
                .filter(UserStore::isDefault)
                .map(UserStore::getStoreId)
                .findFirst()
                .orElse(null);
            return new LocalAccount(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isActive(),
                storeIds,
                defaultStoreId
            );
        });
    }
}
