package com.centralizesys.security;

import com.centralizesys.util.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHandlersTest {

    private ObjectMapper objectMapper;
    private LoggingAuthenticationEntryPoint authenticationEntryPoint;
    private CustomAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        // We use a real ObjectMapper instead of a mock because it's a pure utility
        // and we want to test the actual JSON serialization output.
        objectMapper = new ObjectMapper();

        authenticationEntryPoint = new LoggingAuthenticationEntryPoint(objectMapper);
        accessDeniedHandler = new CustomAccessDeniedHandler(objectMapper);
    }

    @Test
    @DisplayName("AuthenticationEntryPoint should write 401 status and JSON error response directly to buffer")
    void testLoggingAuthenticationEntryPoint() throws IOException {
        // Given (Arrange)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/ventas");
        request.setMethod("GET");

        MockHttpServletResponse response = new MockHttpServletResponse();

        InsufficientAuthenticationException authException =
                new InsufficientAuthenticationException("Full authentication is required");

        // When (Act)
        authenticationEntryPoint.commence(request, response, authException);

        // Then (Assert)
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

        // Deserialize the JSON written to the response body
        Map<String, Object> responseBody = objectMapper.readValue(
                response.getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(responseBody)
                .containsEntry("status", HttpServletResponse.SC_UNAUTHORIZED)
                .containsEntry("message", Constants.ERR_UNAUTHORIZED)
                .containsKey("timestamp");
    }

    @Test
    @DisplayName("AccessDeniedHandler should write 403 status and JSON error response directly to buffer")
    void testCustomAccessDeniedHandler() throws IOException {
        // Given (Arrange)
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AccessDeniedException accessException = new AccessDeniedException("Access is denied");

        // When (Act)
        accessDeniedHandler.handle(request, response, accessException);

        // Then (Assert)
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

        // Deserialize the JSON written to the response body
        Map<String, Object> responseBody = objectMapper.readValue(
                response.getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(responseBody)
                .containsEntry("status", HttpServletResponse.SC_FORBIDDEN)
                .containsEntry("message", Constants.ERR_ACCESS_DENIED)
                .containsKey("timestamp");
    }
}
