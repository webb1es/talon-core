package com.talon.core.stores.internal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStoreRequest(
    @NotBlank String name,
    String address,
    @Size(min = 3, max = 3) String currencyCode,
    @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,
    String timezone,
    String receiptHeader,
    String receiptFooter
) {
}
