package com.talon.core.shared;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {

    public static <T> ItemsResponse<T> of(List<T> items) {
        return new ItemsResponse<>(List.copyOf(items));
    }
}
