package com.talon.core.users.internal.service;

import com.talon.core.users.internal.repository.UserStoreRepository;
import com.talon.core.stores.StoreMemberPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreMemberAdapter implements StoreMemberPort {

    private final UserStoreRepository userStoreRepository;

    @Override
    public boolean hasMembers(UUID storeId) {
        return userStoreRepository.existsByStoreId(storeId);
    }
}
