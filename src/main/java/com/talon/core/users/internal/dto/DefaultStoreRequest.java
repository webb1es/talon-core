package com.talon.core.users.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DefaultStoreRequest(@NotNull UUID storeId) {
}
