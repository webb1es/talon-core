package com.talon.core.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

/**
 * Post-login destination depends on the trigger: a saved request means
 * Swagger initiated the login (return there); no saved request means the SPA
 * did (return to the frontend origin, not talon-core's own API host).
 */
public class SpaAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final AuthenticationSuccessHandler savedRequestHandler = new SavedRequestAwareAuthenticationSuccessHandler();
    private final String frontendUrl;

    public SpaAwareAuthenticationSuccessHandler(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        response.sendRedirect(frontendUrl);
    }
}
