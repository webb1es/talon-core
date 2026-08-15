package com.talon.core.security;

import com.talon.core.domain.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Role hierarchy for who can manage whom. */
@Service
public class Rbac {

    private static final Map<Role, List<Role>> MANAGEABLE_ROLES = Map.of(
        Role.SUPER_ADMIN, List.of(Role.ADMIN),
        Role.ADMIN, List.of(Role.MANAGER, Role.CASHIER),
        Role.MANAGER, List.of(Role.CASHIER),
        Role.CASHIER, List.of()
    );

    public boolean canManage(Role callerRole, Role targetRole) {
        return MANAGEABLE_ROLES.getOrDefault(callerRole, List.of()).contains(targetRole);
    }

    public boolean canAssignRole(Role callerRole, Role newRole) {
        return canManage(callerRole, newRole);
    }

    public boolean hasStoreOverlap(List<UUID> callerStoreIds, List<UUID> targetStoreIds) {
        return callerStoreIds.stream().anyMatch(targetStoreIds::contains);
    }
}
