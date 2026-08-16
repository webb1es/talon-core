package com.talon.core.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        return probe();
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> healthz() {
        return probe();
    }

    /** Rollout canary uses these; a 200 with a dead DB would promote a broken replica. */
    private ResponseEntity<Map<String, String>> probe() {
        try (Connection connection = dataSource.getConnection()) {
            // 1s: gitops liveness timeout defaults to 1s.
            if (!connection.isValid(1)) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
            }
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
    }
}
