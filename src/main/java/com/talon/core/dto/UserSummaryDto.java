package com.talon.core.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSummaryDto(
    UUID id,
    String username,
    String email,
    String phone,
    String displayName,
    String role,
    UUID storeId,
    boolean active,
    String avatarUrl,
    Instant createdAt,
    List<StoreAssignmentDto> storeAssignments
) {
}
