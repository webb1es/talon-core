package com.talon.core.stores.internal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StoreResponse(
    UUID id,
    String name,
    String address,
    String currencyCode,
    BigDecimal taxRate,
    String timezone,
    String receiptHeader,
    String receiptFooter,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
