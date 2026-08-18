package com.talon.core.users.internal.dto;

import java.util.List;
import java.util.UUID;

public record UserOverviewResponse(
    int totalUsers,
    int activeUsers,
    int inactiveUsers,
    int unassignedUsers,
    List<GroupBreakdown> groups,
    List<AttentionUser> attention
) {
    public record GroupBreakdown(String group, int count, int activeCount) {}

    public record AttentionUser(
        UUID id,
        String displayName,
        String email,
        String group,
        String reason
    ) {}
}
