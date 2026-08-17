package com.talon.core.shared;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps versioned API payloads as {@code { "data": ... }}. RFC 7807
 * {@link ProblemDetail} and empty 204 bodies are left untouched.
 */
@ControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        Class<?> type = returnType.getParameterType();
        return type != void.class && type != Void.class;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof ApiResponse<?> || body instanceof ProblemDetail) {
            return body;
        }
        if (!isVersionedApi(request)) {
            return body;
        }
        return ApiResponse.of(body);
    }

    private static boolean isVersionedApi(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path != null && path.contains("/api/v1/");
    }
}
