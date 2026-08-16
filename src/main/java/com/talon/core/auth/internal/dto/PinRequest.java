package com.talon.core.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record PinRequest(@NotBlank String pin) {
}
