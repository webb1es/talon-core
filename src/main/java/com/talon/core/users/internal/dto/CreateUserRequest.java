package com.talon.core.users.internal.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
    @NotBlank String username,
    String email,
    String phone,
    @NotBlank String displayName,
    @NotBlank String role,
    List<UUID> storeIds
) {
}
