package com.shinyoung.recruit.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = "Authentication is required.";

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(MESSAGE));
    }
}
