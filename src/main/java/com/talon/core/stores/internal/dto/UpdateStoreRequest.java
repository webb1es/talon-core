package com.talon.core.stores.internal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateStoreRequest(
    @Size(min = 1) String name,
    String address,
    @Size(min = 3, max = 3) String currencyCode,
    @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,
    String timezone,
    String receiptHeader,
    String receiptFooter,
    Boolean active
) {
}
