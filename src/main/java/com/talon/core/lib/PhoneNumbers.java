package com.talon.core.lib;

import java.util.Optional;

public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static Optional<String> normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        String subscriber = phone.replaceAll("[\\s\\-().]", "");

        if (subscriber.startsWith("+263") && subscriber.length() == 13) {
            return Optional.of(subscriber);
        }
        if (subscriber.startsWith("263") && subscriber.length() == 12) {
            return Optional.of("+" + subscriber);
        }
        if (subscriber.startsWith("0") && subscriber.length() == 10) {
            return Optional.of("+263" + subscriber.substring(1));
        }
        if (subscriber.length() == 9) {
            return Optional.of("+263" + subscriber);
        }
        return Optional.empty();
    }
}
