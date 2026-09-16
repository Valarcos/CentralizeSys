package com.centralizesys.security;

import com.centralizesys.exception.GlobalExceptionHandler;
import com.centralizesys.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");

        GlobalExceptionHandler.ErrorResponse errorDetails =
                new GlobalExceptionHandler.ErrorResponse(
                        HttpServletResponse.SC_FORBIDDEN,
                        Constants.ERR_ACCESS_DENIED,
                        System.currentTimeMillis()
                );

        objectMapper.writeValue(response.getWriter(), errorDetails);
    }
}
