package com.talon.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/readyz")
    public Map<String, String> readyz() {
        return Map.of("status", "UP");
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "UP");
    }
}
