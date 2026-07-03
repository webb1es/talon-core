package com.talon.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @GetMapping("/api/public")
    public Map<String, String> getPublic() {
        log.info("Accessing public endpoint");
        return Map.of("message", "This is a public endpoint");
    }

    @GetMapping("/api/secured")
    public Map<String, Object> getSecured(@AuthenticationPrincipal Jwt jwt) {
        log.info("Accessing secured endpoint. User subject: {}", jwt.getSubject());
        return Map.of(
            "message", "This is a secured endpoint",
            "subject", jwt.getSubject(),
            "claims", jwt.getClaims()
        );
    }

    @GetMapping("/api/admin")
    @PreAuthorize("hasRole('admin')")
    public Map<String, String> getAdmin() {
        log.info("Accessing admin endpoint");
        return Map.of("message", "This is an admin-only endpoint");
    }

    @GetMapping("/readyz")
    public Map<String, String> readyz() {
        return Map.of("status", "UP");
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "UP");
    }
}
