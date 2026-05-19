package com.shinyoung.recruit.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "Access is denied.";

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(MESSAGE));
    }
}
