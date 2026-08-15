package com.talon.core.dto;

import java.util.UUID;

public record StoreAssignmentDto(UUID storeId, String storeName, boolean isDefault) {
}
