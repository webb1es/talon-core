package com.talon.core.auth.internal.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;

/**
 * Only Swagger login has a saved request worth restoring. The SPA's
 * /api/v1/users/me probe is also saved by oauth2Login, and sending the browser
 * there after login lands on an API JSON page instead of the dashboard.
 */
@RequiredArgsConstructor
public class SpaAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final AuthenticationSuccessHandler savedRequestHandler = new SavedRequestAwareAuthenticationSuccessHandler();
    private final String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && isSwaggerReturn(savedRequest)) {
            savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        if (savedRequest != null) {
            requestCache.removeRequest(request, response);
        }
        response.sendRedirect(trimTrailingSlash(frontendUrl) + "/dashboard");
    }

    private static boolean isSwaggerReturn(SavedRequest savedRequest) {
        String redirectUrl = savedRequest.getRedirectUrl();
        if (redirectUrl == null) {
            return false;
        }
        try {
            String path = URI.create(redirectUrl).getPath();
            return path != null && (path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
