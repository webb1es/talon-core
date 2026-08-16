package com.talon.core.controller;

import com.talon.core.domain.Store;
import com.talon.core.domain.User;
import com.talon.core.domain.UserStore;
import com.talon.core.dto.SessionInfoResponse;
import com.talon.core.dto.StoreAssignmentDto;
import com.talon.core.repository.StoreRepository;
import com.talon.core.repository.UserRepository;
import com.talon.core.repository.UserStoreRepository;
import com.talon.core.security.CurrentUser;
import com.talon.core.security.CurrentUserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No password endpoints: Keycloak owns credentials, and there's no local
 * path that needs to verify one.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final UserStoreRepository userStoreRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, UserStoreRepository userStoreRepository,
                              StoreRepository storeRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userStoreRepository = userStoreRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/session-info")
    public SessionInfoResponse sessionInfo(@CurrentUser CurrentUserProfile currentUser) {
        User user = requireUser(currentUser.id());
        List<UserStore> assignments = userStoreRepository.findByUserId(user.getId());
        List<UUID> storeIds = assignments.stream().map(UserStore::getStoreId).toList();
        Map<UUID, String> storeNames = storeRepository.findAllById(storeIds).stream()
            .collect(java.util.stream.Collectors.toMap(Store::getId, Store::getName));

        List<StoreAssignmentDto> storeAssignments = assignments.stream()
            .map(a -> new StoreAssignmentDto(
                a.getStoreId(),
                storeNames.getOrDefault(a.getStoreId(), "Unknown"),
                a.isDefault()))
            .toList();

        return new SessionInfoResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            currentUser.role(),
            user.getAvatarUrl(),
            currentUser.defaultStoreId(),
            storeAssignments,
            user.getPin() != null
        );
    }

    public record DisplayNameRequest(String displayName) {}

    @PutMapping("/display-name")
    public Map<String, Boolean> updateDisplayName(@CurrentUser CurrentUserProfile currentUser, @RequestBody DisplayNameRequest body) {
        User user = requireUser(currentUser.id());
        user.setDisplayName(body.displayName());
        userRepository.save(user);
        return Map.of("success", true);
    }

    public record PinRequest(String pin) {}

    @PostMapping("/pin")
    public Map<String, Boolean> setPin(@CurrentUser CurrentUserProfile currentUser, @RequestBody PinRequest body) {
        User user = requireUser(currentUser.id());
        user.setPin(passwordEncoder.encode(body.pin()));
        userRepository.save(user);
        return Map.of("success", true);
    }

    @DeleteMapping("/pin")
    public Map<String, Boolean> removePin(@CurrentUser CurrentUserProfile currentUser) {
        User user = requireUser(currentUser.id());
        user.setPin(null);
        userRepository.save(user);
        return Map.of("success", true);
    }

    @PostMapping("/pin/verify")
    public Map<String, Boolean> verifyPin(@CurrentUser CurrentUserProfile currentUser, @RequestBody PinRequest body) {
        User user = requireUser(currentUser.id());
        boolean valid = user.getPin() != null && passwordEncoder.matches(body.pin(), user.getPin());
        return Map.of("valid", valid);
    }

    public record StoreRequest(UUID storeId) {}

    @PutMapping("/store")
    @Transactional
    public Map<String, Boolean> switchStore(@CurrentUser CurrentUserProfile currentUser, @RequestBody StoreRequest body) {
        if (!currentUser.storeIds().contains(body.storeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store is not assigned to you");
        }
        List<UserStore> assignments = userStoreRepository.findByUserId(currentUser.id());
        for (UserStore assignment : assignments) {
            assignment.setDefault(assignment.getStoreId().equals(body.storeId()));
        }
        userStoreRepository.saveAll(assignments);
        return Map.of("success", true);
    }

    public record AvatarRequest(String avatarUrl) {}

    @PutMapping("/avatar")
    public Map<String, Boolean> updateAvatar(@CurrentUser CurrentUserProfile currentUser, @RequestBody AvatarRequest body) {
        if (body.avatarUrl() == null || body.avatarUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid avatar URL");
        }
        User user = requireUser(currentUser.id());
        user.setAvatarUrl(body.avatarUrl());
        userRepository.save(user);
        return Map.of("success", true);
    }

    @DeleteMapping("/avatar")
    public Map<String, Boolean> removeAvatar(@CurrentUser CurrentUserProfile currentUser) {
        User user = requireUser(currentUser.id());
        user.setAvatarUrl(null);
        userRepository.save(user);
        return Map.of("success", true);
    }
}
