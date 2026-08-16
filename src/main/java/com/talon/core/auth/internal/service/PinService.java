package com.talon.core.auth.internal.service;

import com.talon.core.auth.PinCredentialsPort;
import com.talon.core.auth.PinVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PinService implements PinVerificationPort {

    private final PinCredentialsPort pinCredentials;
    private final PasswordEncoder passwordEncoder;

    public void set(UUID userId, String pin) {
        pinCredentials.replaceHash(userId, passwordEncoder.encode(pin));
    }

    public void clear(UUID userId) {
        pinCredentials.clearHash(userId);
    }

    @Override
    public boolean verify(UUID userId, String pin) {
        return pinCredentials.findHash(userId)
            .map(hash -> passwordEncoder.matches(pin, hash))
            .orElse(false);
    }
}
