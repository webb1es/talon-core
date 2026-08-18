package com.talon.core.auth.internal.config;

import com.talon.core.auth.internal.web.ApiAccessDeniedHandler;
import com.talon.core.auth.internal.web.ApiAuthenticationEntryPoint;
import com.talon.core.auth.internal.web.SpaAwareAuthenticationSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * talon-core is the sole OAuth2 client (BFF pattern): the browser only ever
 * holds the httpOnly session cookie this chain issues via oauth2Login, never
 * a Keycloak token. No resource-server/bearer-JWT chain exists.
 *
 * CSRF is disabled deliberately: SameSite=Lax is the CSRF defense here. The
 * frontend and this API are different origins but the same registrable site
 * (mytalon.co.zw), so the cookie flows between them; a genuine cross-site
 * attacker page is on a different site and never gets it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     SpaAwareAuthenticationSuccessHandler successHandler,
                                                     LogoutSuccessHandler logoutSuccessHandler,
                                                     CustomOidcUserService customOidcUserService,
                                                     ObjectMapper objectMapper) throws Exception {
        ApiAuthenticationEntryPoint apiEntryPoint = new ApiAuthenticationEntryPoint(objectMapper);
        ApiAccessDeniedHandler apiAccessDeniedHandler = new ApiAccessDeniedHandler(objectMapper);

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(apiEntryPoint, new AntPathRequestMatcher("/api/**"))
                .defaultAccessDeniedHandlerFor(apiAccessDeniedHandler, new AntPathRequestMatcher("/api/**"))
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/readyz", "/healthz").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("manage_system_settings")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(successHandler)
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessHandler(logoutSuccessHandler)
            );
        return http.build();
    }

    @Bean
    public SpaAwareAuthenticationSuccessHandler spaAwareAuthenticationSuccessHandler() {
        return new SpaAwareAuthenticationSuccessHandler(frontendUrl);
    }

    /** PIN hashing only — Keycloak owns the account password. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
            new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        // Absolute URI — the frontend is a different origin from talon-core, so
        // this can't be expressed as a {baseUrl}-relative path.
        handler.setPostLogoutRedirectUri(frontendUrl + "/login");
        return handler;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors-allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Client-credentials token manager for talon-core-admin (Keycloak Admin
     * REST API access). Not tied to any end-user request, so the request-bound
     * DefaultOAuth2AuthorizedClientManager doesn't apply here.
     */
    @Bean
    public OAuth2AuthorizedClientManager keycloakAdminAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {
        OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        InMemoryOAuth2AuthorizedClientService clientService =
            new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }
}
