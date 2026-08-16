package com.talon.core.users.internal.dto;

import java.util.UUID;

public record StoreAssignmentDto(UUID storeId, String storeName, boolean isDefault) {
}
