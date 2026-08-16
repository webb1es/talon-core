package com.talon.core.auth;

import java.util.UUID;

public interface PinVerificationPort {

    boolean verify(UUID userId, String pin);
}
