package com.talon.core.controller;

import com.talon.core.domain.Role;
import com.talon.core.domain.Store;
import com.talon.core.domain.User;
import com.talon.core.domain.UserStore;
import com.talon.core.dto.StoreAssignmentDto;
import com.talon.core.dto.UserSummaryDto;
import com.talon.core.lib.PhoneNumbers;
import com.talon.core.repository.StoreRepository;
import com.talon.core.repository.UserRepository;
import com.talon.core.repository.UserStoreRepository;
import com.talon.core.security.CurrentUser;
import com.talon.core.security.CurrentUserProfile;
import com.talon.core.security.KeycloakAdminClientService;
import com.talon.core.security.Rbac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Team-management endpoints — writes Keycloak (identity, credentials, role)
 * and local User/UserStore rows (PIN, store assignments, profile).
 */
@RestController
@RequestMapping("/api/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;
    private final StoreRepository storeRepository;
    private final KeycloakAdminClientService keycloakAdminClientService;
    private final Rbac rbac;

    public AdminUserController(UserRepository userRepository, UserStoreRepository userStoreRepository,
                                StoreRepository storeRepository, KeycloakAdminClientService keycloakAdminClientService,
                                Rbac rbac) {
        this.userRepository = userRepository;
        this.userStoreRepository = userStoreRepository;
        this.storeRepository = storeRepository;
        this.keycloakAdminClientService = keycloakAdminClientService;
        this.rbac = rbac;
    }

    private Role currentUserRole(CurrentUserProfile currentUser) {
        return Role.fromValue(currentUser.role());
    }

    private static String generateTempPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @GetMapping
    public List<UserSummaryDto> list(@CurrentUser CurrentUserProfile currentUser) {
        Role currentUserRole = currentUserRole(currentUser);
        List<User> allUsers = userRepository.findAll();
        List<UserStore> allAssignments = userStoreRepository.findAll();

        Map<UUID, List<UserStore>> assignmentsByUser = allAssignments.stream()
            .collect(Collectors.groupingBy(UserStore::getUserId));
        Map<UUID, String> storeNames = storeRepository.findAll().stream()
            .collect(Collectors.toMap(Store::getId, Store::getName));

        return allUsers.stream()
            .filter(u -> !u.getId().equals(currentUser.id()))
            .filter(u -> rbac.canManage(currentUserRole, u.getRole()))
            .filter(u -> {
                if (currentUserRole != Role.MANAGER) return true;
                List<UUID> targetStoreIds = assignmentsByUser.getOrDefault(u.getId(), List.of()).stream()
                    .map(UserStore::getStoreId).toList();
                return rbac.hasStoreOverlap(currentUser.storeIds(), targetStoreIds);
            })
            .map(u -> toSummary(u, assignmentsByUser.getOrDefault(u.getId(), List.of()), storeNames))
            .toList();
    }

    private UserSummaryDto toSummary(User u, List<UserStore> assignments, Map<UUID, String> storeNames) {
        List<StoreAssignmentDto> assignmentDtos = assignments.stream()
            .map(a -> new StoreAssignmentDto(a.getStoreId(), storeNames.getOrDefault(a.getStoreId(), "Unknown"), a.isDefault()))
            .toList();
        UUID defaultStoreId = assignments.stream().filter(UserStore::isDefault).map(UserStore::getStoreId).findFirst().orElse(null);
        return new UserSummaryDto(u.getId(), u.getUsername(), u.getEmail(), u.getPhone(), u.getDisplayName(),
            u.getRole().getValue(), defaultStoreId, u.isActive(), u.getAvatarUrl(), u.getCreatedAt(), assignmentDtos);
    }

    @GetMapping("/stores")
    public List<Map<String, Object>> listStores(@CurrentUser CurrentUserProfile currentUser) {
        Role currentUserRole = currentUserRole(currentUser);
        List<Store> stores = (currentUserRole == Role.MANAGER && !currentUser.storeIds().isEmpty())
            ? storeRepository.findAllById(currentUser.storeIds())
            : storeRepository.findAll();
        return stores.stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName()))
            .toList();
    }

    public record CreateUserRequest(String username, String email, String phone, String displayName,
                                     String role, UUID storeId, List<UUID> storeIds) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, UUID>> create(@CurrentUser CurrentUserProfile currentUser, @RequestBody CreateUserRequest body) {
        Role currentUserRole = currentUserRole(currentUser);
        Role targetRole = Role.fromValue(body.role());
        if (!rbac.canManage(currentUserRole, targetRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to create this role");
        }
        if (body.username() == null || body.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String username = body.username().toLowerCase();
        Optional<String> normalizedPhone = PhoneNumbers.normalize(body.phone());

        // Keycloak enforces username/email uniqueness — createUser() surfaces
        // a conflict itself rather than this pre-checking a separate cache.
        String keycloakId = null;
        try {
            keycloakId = keycloakAdminClientService.createUser(username, body.email(), generateTempPassword(), true);
            keycloakAdminClientService.assignRealmRole(keycloakId, targetRole);

            User user = new User();
            user.setKeycloakId(UUID.fromString(keycloakId));
            user.setUsername(username);
            user.setEmail(body.email());
            normalizedPhone.ifPresent(user::setPhone);
            user.setDisplayName(body.displayName());
            user.setRole(targetRole);
            user.setActive(true);
            user = userRepository.save(user);

            List<UUID> resolvedStoreIds = (body.storeIds() != null && !body.storeIds().isEmpty())
                ? body.storeIds()
                : (body.storeId() != null ? List.of(body.storeId()) : List.of());
            saveStoreAssignments(user.getId(), resolvedStoreIds);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", user.getId()));
        } catch (RuntimeException e) {
            if (keycloakId != null) {
                try {
                    keycloakAdminClientService.deleteUser(keycloakId);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local create failed", keycloakId, cleanup);
                }
            }
            throw e;
        }
    }

    public record UpdateUserRequest(String displayName, String email, String phone, String role,
                                     UUID storeId, List<UUID> storeIds, Boolean active) {}

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Boolean> update(@CurrentUser CurrentUserProfile currentUser, @PathVariable UUID id,
                                        @RequestBody UpdateUserRequest body) {
        Role currentUserRole = currentUserRole(currentUser);
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UUID> targetStoreIds = userStoreRepository.findByUserId(id).stream().map(UserStore::getStoreId).toList();
        boolean canManageTarget = rbac.canManage(currentUserRole, target.getRole())
            && (currentUserRole != Role.MANAGER || rbac.hasStoreOverlap(currentUser.storeIds(), targetStoreIds));
        if (!canManageTarget) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this user");
        }

        Role previousRole = target.getRole();
        Role newRole = body.role() != null ? Role.fromValue(body.role()) : null;
        if (newRole != null && newRole != previousRole && !rbac.canAssignRole(currentUserRole, newRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot assign this role");
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
                previousKeycloakUser = keycloakAdminClientService.getUser(keycloakId);
            }
            if (!keycloakUpdate.isEmpty()) {
                keycloakAdminClientService.updateUser(keycloakId, keycloakUpdate);
                keycloakChanged = true;
            }
            if (newRole != null) {
                keycloakAdminClientService.assignRealmRole(keycloakId, newRole);
                keycloakChanged = true;
            }

            userRepository.save(target);
            if (body.storeIds() != null || body.storeId() != null) {
                List<UUID> resolvedStoreIds = (body.storeIds() != null) ? body.storeIds()
                    : List.of(body.storeId());
                saveStoreAssignments(id, resolvedStoreIds);
            }
        } catch (RuntimeException e) {
            if (keycloakChanged) {
                try {
                    keycloakAdminClientService.replaceUser(keycloakId, previousKeycloakUser);
                    keycloakAdminClientService.assignRealmRole(keycloakId, previousRole);
                } catch (RuntimeException cleanup) {
                    log.error("Failed to roll back Keycloak user {} after local update failed", keycloakId, cleanup);
                }
            }
            throw e;
        }

        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Boolean> delete(@CurrentUser CurrentUserProfile currentUser, @PathVariable UUID id) {
        if (id.equals(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete your own account");
        }
        Role currentUserRole = currentUserRole(currentUser);
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        List<UUID> targetStoreIds = userStoreRepository.findByUserId(id).stream().map(UserStore::getStoreId).toList();
        boolean canManageTarget = rbac.canManage(currentUserRole, target.getRole())
            && (currentUserRole != Role.MANAGER || rbac.hasStoreOverlap(currentUser.storeIds(), targetStoreIds));
        if (!canManageTarget) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this user");
        }

        // Local first so a Keycloak failure rolls the row back; Keycloak-first
        // would leave a local user whose identity is already gone.
        userStoreRepository.deleteAll(userStoreRepository.findByUserId(id));
        userRepository.delete(target);
        keycloakAdminClientService.deleteUser(target.getKeycloakId().toString());
        return Map.of("success", true);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, Boolean> resetPassword(@CurrentUser CurrentUserProfile currentUser, @PathVariable UUID id) {
        Role currentUserRole = currentUserRole(currentUser);
        if (currentUserRole != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only super admins can reset passwords");
        }
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!rbac.canManage(currentUserRole, target.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to reset this user's password");
        }

        keycloakAdminClientService.resetPassword(target.getKeycloakId().toString(), generateTempPassword());
        return Map.of("success", true);
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
}
