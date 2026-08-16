package com.talon.core.users.internal.service;

import com.talon.core.users.internal.dto.CreateUserRequest;
import com.talon.core.users.internal.entity.Role;
import com.talon.core.users.internal.dto.StoreAssignmentDto;
import com.talon.core.users.internal.dto.UpdateUserRequest;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.repository.UserRepository;
import com.talon.core.users.internal.dto.UserResponse;
import com.talon.core.users.internal.entity.UserStore;
import com.talon.core.users.internal.repository.UserStoreRepository;
import com.talon.core.users.internal.policy.Rbac;
import com.talon.core.users.internal.vo.PhoneNumbers;
import com.talon.core.auth.CurrentUserProfile;
import com.talon.core.auth.KeycloakAdminPort;
import com.talon.core.shared.BadRequestException;
import com.talon.core.shared.NotFoundException;
import com.talon.core.stores.StorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;
    private final StorePort storePort;
    private final KeycloakAdminPort keycloakAdminPort;
    private final Rbac rbac;

    @Transactional(readOnly = true)
    public List<UserResponse> list(CurrentUserProfile currentUser) {
        Role currentUserRole = roleOf(currentUser);
        List<User> allUsers = userRepository.findAll();
        List<UserStore> allAssignments = userStoreRepository.findAll();

        Map<UUID, List<UserStore>> assignmentsByUser = allAssignments.stream()
            .collect(Collectors.groupingBy(UserStore::getUserId));
        Map<UUID, String> storeNames = storePort.findAllById(
                allAssignments.stream().map(UserStore::getStoreId).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(StorePort.StoreSummary::id, StorePort.StoreSummary::name));

        return allUsers.stream()
            .filter(u -> !u.getId().equals(currentUser.id()))
            .filter(u -> rbac.canManage(currentUserRole, u.getRole()))
            .filter(u -> {
                if (currentUserRole != Role.MANAGER) {
                    return true;
                }
                List<UUID> targetStoreIds = assignmentsByUser.getOrDefault(u.getId(), List.of()).stream()
                    .map(UserStore::getStoreId).toList();
                return rbac.hasStoreOverlap(currentUser.storeIds(), targetStoreIds);
            })
            .map(u -> toResponse(u, assignmentsByUser.getOrDefault(u.getId(), List.of()), storeNames))
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(CurrentUserProfile currentUser, UUID id) {
        User target = requireUser(id);
        requireCanManage(currentUser, target);
        List<UserStore> assignments = userStoreRepository.findByUserId(id);
        Map<UUID, String> storeNames = storePort.findAllById(
                assignments.stream().map(UserStore::getStoreId).toList()
            ).stream()
            .collect(Collectors.toMap(StorePort.StoreSummary::id, StorePort.StoreSummary::name));
        return toResponse(target, assignments, storeNames);
    }

    @Transactional
    public UserResponse create(CurrentUserProfile currentUser, CreateUserRequest body) {
        Role currentUserRole = roleOf(currentUser);
        Role targetRole = Role.fromValue(body.role());
        if (!rbac.canManage(currentUserRole, targetRole)) {
            throw new AccessDeniedException("You do not have permission to create this role");
        }
        String username = body.username().toLowerCase();
        Optional<String> normalizedPhone = PhoneNumbers.normalize(body.phone());
        List<UUID> resolvedStoreIds = resolveStoreIds(body.storeIds(), body.storeId());
        requireKnownStores(resolvedStoreIds);

        String keycloakId = null;
        try {
            keycloakId = keycloakAdminPort.createUser(username, body.email(), generateTempPassword(), true);
            keycloakAdminPort.assignRealmRole(keycloakId, targetRole.getValue());

            User user = new User();
            user.setKeycloakId(UUID.fromString(keycloakId));
            user.setUsername(username);
            user.setEmail(body.email());
            normalizedPhone.ifPresent(user::setPhone);
            user.setDisplayName(body.displayName());
            user.setRole(targetRole);
            user.setActive(true);
            user = userRepository.save(user);
            saveStoreAssignments(user.getId(), resolvedStoreIds);
            List<UserStore> assignments = userStoreRepository.findByUserId(user.getId());
            Map<UUID, String> storeNames = storePort.findAllById(resolvedStoreIds).stream()
                .collect(Collectors.toMap(StorePort.StoreSummary::id, StorePort.StoreSummary::name));
            return toResponse(user, assignments, storeNames);
        } catch (RuntimeException e) {
            if (keycloakId != null) {
                try {
                    keycloakAdminPort.deleteUser(keycloakId);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local create failed", keycloakId, cleanup);
                }
            }
            throw e;
        }
    }

    @Transactional
    public UserResponse update(CurrentUserProfile currentUser, UUID id, UpdateUserRequest body) {
        User target = requireUser(id);
        requireCanManage(currentUser, target);

        Role currentUserRole = roleOf(currentUser);
        Role previousRole = target.getRole();
        Role newRole = body.role() != null ? Role.fromValue(body.role()) : null;
        if (newRole != null && newRole != previousRole && !rbac.canAssignRole(currentUserRole, newRole)) {
            throw new AccessDeniedException("You cannot assign this role");
        }

        Map<String, Object> keycloakUpdate = new HashMap<>();
        if (body.displayName() != null) {
            target.setDisplayName(body.displayName());
        }
        if (body.email() != null) {
            target.setEmail(body.email().isBlank() ? null : body.email());
            keycloakUpdate.put("email", target.getEmail());
            keycloakUpdate.put("emailVerified", true);
        }
        if (body.phone() != null) {
            target.setPhone(PhoneNumbers.normalize(body.phone()).orElse(null));
        }
        if (body.active() != null) {
            target.setActive(body.active());
            keycloakUpdate.put("enabled", body.active());
        }
        if (newRole != null) {
            target.setRole(newRole);
        }

        String keycloakId = target.getKeycloakId().toString();
        Map<String, Object> previousKeycloakUser = null;
        boolean keycloakChanged = false;
        try {
            if (!keycloakUpdate.isEmpty() || newRole != null) {
                previousKeycloakUser = keycloakAdminPort.getUser(keycloakId);
            }
            if (!keycloakUpdate.isEmpty()) {
                keycloakAdminPort.updateUser(keycloakId, keycloakUpdate);
                keycloakChanged = true;
            }
            if (newRole != null) {
                keycloakAdminPort.assignRealmRole(keycloakId, newRole.getValue());
                keycloakChanged = true;
            }

            userRepository.save(target);
            if (body.storeIds() != null || body.storeId() != null) {
                List<UUID> resolvedStoreIds = resolveStoreIds(body.storeIds(), body.storeId());
                requireKnownStores(resolvedStoreIds);
                saveStoreAssignments(id, resolvedStoreIds);
            }
        } catch (RuntimeException e) {
            if (keycloakChanged) {
                try {
                    keycloakAdminPort.replaceUser(keycloakId, previousKeycloakUser);
                    keycloakAdminPort.assignRealmRole(keycloakId, previousRole.getValue());
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local update failed", keycloakId, cleanup);
                }
            }
            throw e;
        }

        return get(currentUser, id);
    }

    @Transactional
    public void delete(CurrentUserProfile currentUser, UUID id) {
        if (id.equals(currentUser.id())) {
            throw new AccessDeniedException("Cannot delete your own account");
        }
        User target = requireUser(id);
        requireCanManage(currentUser, target);

        userStoreRepository.deleteAll(userStoreRepository.findByUserId(id));
        userRepository.delete(target);
        keycloakAdminPort.deleteUser(target.getKeycloakId().toString());
    }

    public void resetPassword(CurrentUserProfile currentUser, UUID id) {
        if (roleOf(currentUser) != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Only super admins can reset passwords");
        }
        User target = requireUser(id);
        if (!rbac.canManage(roleOf(currentUser), target.getRole())) {
            throw new AccessDeniedException("You do not have permission to reset this user's password");
        }
        keycloakAdminPort.resetPassword(target.getKeycloakId().toString(), generateTempPassword());
    }

    private void requireCanManage(CurrentUserProfile currentUser, User target) {
        Role currentUserRole = roleOf(currentUser);
        List<UUID> targetStoreIds = userStoreRepository.findByUserId(target.getId()).stream()
            .map(UserStore::getStoreId).toList();
        boolean canManageTarget = rbac.canManage(currentUserRole, target.getRole())
            && (currentUserRole != Role.MANAGER || rbac.hasStoreOverlap(currentUser.storeIds(), targetStoreIds));
        if (!canManageTarget) {
            throw new AccessDeniedException("You do not have permission to manage this user");
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static Role roleOf(CurrentUserProfile currentUser) {
        return Role.fromValue(currentUser.role());
    }

    private static String generateTempPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static List<UUID> resolveStoreIds(List<UUID> storeIds, UUID storeId) {
        if (storeIds != null && !storeIds.isEmpty()) {
            return storeIds;
        }
        return storeId != null ? List.of(storeId) : List.of();
    }

    private void requireKnownStores(List<UUID> storeIds) {
        for (UUID storeId : storeIds) {
            if (!storePort.exists(storeId)) {
                throw new BadRequestException("Unknown store: " + storeId);
            }
        }
    }

    private void saveStoreAssignments(UUID userId, List<UUID> storeIds) {
        userStoreRepository.deleteAll(userStoreRepository.findByUserId(userId));
        for (int i = 0; i < storeIds.size(); i++) {
            UserStore assignment = new UserStore();
            assignment.setUserId(userId);
            assignment.setStoreId(storeIds.get(i));
            assignment.setDefault(i == 0);
            userStoreRepository.save(assignment);
        }
    }

    private UserResponse toResponse(User user, List<UserStore> assignments, Map<UUID, String> storeNames) {
        List<StoreAssignmentDto> assignmentDtos = assignments.stream()
            .map(a -> new StoreAssignmentDto(
                a.getStoreId(),
                storeNames.getOrDefault(a.getStoreId(), "Unknown"),
                a.isDefault()))
            .toList();
        UUID defaultStoreId = assignments.stream()
            .filter(UserStore::isDefault)
            .map(UserStore::getStoreId)
            .findFirst()
            .orElse(null);
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPhone(),
            user.getDisplayName(),
            user.getRole().getValue(),
            defaultStoreId,
            user.isActive(),
            user.getAvatarUrl(),
            user.getCreatedAt(),
            assignmentDtos
        );
    }
}
