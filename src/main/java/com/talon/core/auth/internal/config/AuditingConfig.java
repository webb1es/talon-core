package com.talon.core.auth.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;
import java.util.UUID;

/**
 * createdBy/updatedBy read straight off the OidcUser principal's subject,
 * which is User.id (see CurrentUserService) — no DB lookup needed.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(oidcUser.getSubject()));
        };
    }
}
