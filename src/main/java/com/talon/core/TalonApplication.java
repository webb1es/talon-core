package com.talon.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@Slf4j
@RequiredArgsConstructor
@SpringBootApplication
public class TalonApplication {

    private final Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(TalonApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSwaggerUrl() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        log.info("Swagger UI: http://localhost:{}/swagger-ui.html", port);
    }
}
