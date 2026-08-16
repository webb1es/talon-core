package com.talon.core.users.internal.service;

import com.talon.core.auth.PinCredentialsPort;
import com.talon.core.shared.NotFoundException;
import com.talon.core.users.internal.entity.User;
import com.talon.core.users.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PinCredentialsAdapter implements PinCredentialsPort {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findHash(UUID userId) {
        return userRepository.findById(userId).map(User::getPin);
    }

    @Override
    @Transactional
    public void replaceHash(UUID userId, String pinHash) {
        User user = requireUser(userId);
        user.setPin(pinHash);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void clearHash(UUID userId) {
        User user = requireUser(userId);
        user.setPin(null);
        userRepository.save(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
