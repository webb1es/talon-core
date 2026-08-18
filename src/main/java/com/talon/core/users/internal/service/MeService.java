package com.talon.core.users.internal.service;

import com.talon.core.users.internal.dto.MeResponse;
import com.talon.core.users.internal.dto.PatchMeRequest;
import com.talon.core.users.internal.dto.StoreAssignmentDto;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.repository.UserRepository;
import com.talon.core.users.internal.entity.UserStore;
import com.talon.core.users.internal.repository.UserStoreRepository;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.shared.BadRequestException;
import com.talon.core.shared.NotFoundException;
import com.talon.core.stores.StorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;
    private final StorePort storePort;

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUserProfile currentUser) {
        User user = requireUser(currentUser.id());
        List<UserStore> assignments = userStoreRepository.findByUserId(user.getId());
        Map<UUID, String> storeNames = storeNames(assignments);
        List<StoreAssignmentDto> storeAssignments = assignments.stream()
            .map(a -> new StoreAssignmentDto(
                a.getStoreId(),
                storeNames.getOrDefault(a.getStoreId(), "Unknown"),
                a.isDefault()))
            .toList();
        return new MeResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            currentUser.group(),
            livePermissions(),
            user.getAvatarUrl(),
            storeAssignments,
            user.getPin() != null
        );
    }

    /** The granular roles the live token actually carries — what the API enforces, exposed for FE button-level guards. */
    private List<String> livePermissions() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .toList();
    }

    @Transactional
    public MeResponse patch(CurrentUserProfile currentUser, PatchMeRequest request) {
        User user = requireUser(currentUser.id());
        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new BadRequestException("Display name is required");
            }
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        userRepository.save(user);
        return me(currentUser);
    }

    @Transactional
    public void switchDefaultStore(CurrentUserProfile currentUser, UUID storeId) {
        if (!currentUser.storeIds().contains(storeId)) {
            throw new BadRequestException("Store is not assigned to you");
        }
        List<UserStore> assignments = userStoreRepository.findByUserId(currentUser.id());
        for (UserStore assignment : assignments) {
            assignment.setDefault(assignment.getStoreId().equals(storeId));
        }
        userStoreRepository.saveAll(assignments);
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Map<UUID, String> storeNames(List<UserStore> assignments) {
        List<UUID> storeIds = assignments.stream().map(UserStore::getStoreId).toList();
        return storePort.findAllById(storeIds).stream()
            .collect(Collectors.toMap(StorePort.StoreSummary::id, StorePort.StoreSummary::name));
    }
}
