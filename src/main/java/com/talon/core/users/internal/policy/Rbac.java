package com.talon.core.users.internal.policy;

import com.talon.core.users.internal.entity.Group;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class Rbac {

    private static final Map<Group, List<Group>> MANAGEABLE_GROUPS = Map.of(
        Group.SUPER_ADMIN, List.of(Group.ADMIN),
        Group.ADMIN, List.of(Group.MANAGER, Group.CASHIER),
        Group.MANAGER, List.of(Group.CASHIER),
        Group.CASHIER, List.of()
    );

    public boolean canManage(Group callerGroup, Group targetGroup) {
        return MANAGEABLE_GROUPS.getOrDefault(callerGroup, List.of()).contains(targetGroup);
    }

    public boolean canAssignGroup(Group callerGroup, Group newGroup) {
        return canManage(callerGroup, newGroup);
    }

    public boolean hasStoreOverlap(List<UUID> callerStoreIds, List<UUID> targetStoreIds) {
        return callerStoreIds.stream().anyMatch(targetStoreIds::contains);
    }
}
