package com.centralizesys.security;

import com.centralizesys.model.auth.ApiKey;
import com.centralizesys.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyService, new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonExternalPathPassesThroughWithoutApiKeyValidation() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/productos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(apiKeyService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void externalPathWithoutApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/external/catalogo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Cabecera X-API-KEY no provista o vacía."));
        verifyNoInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void externalPathWithInvalidApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/external/catalogo");
        request.addHeader("X-API-KEY", "cs_live_invalidkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyService.validateKey("cs_live_invalidkey123")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("API Key inválida o inactiva."));
        verifyNoInteractions(filterChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void externalPathWithValidApiKeyAuthenticatesWithRoleExternalService() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/external/catalogo");
        request.addHeader("X-API-KEY", "cs_live_validkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiKey apiKey = ApiKey.builder()
                .id(1L)
                .nombre("Marketing Agency")
                .rateLimitRpm(10)
                .activo(true)
                .build();

        when(apiKeyService.validateKey("cs_live_validkey123")).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(apiKeyService, times(1)).recordAccessThrottled(1L);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("Marketing Agency", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EXTERNAL_SERVICE")));
    }

    @Test
    void externalPathExceedingRateLimitReturns429WithRetryAfter() throws ServletException, IOException {
        ApiKey apiKey = ApiKey.builder()
                .id(2L)
                .nombre("Throttled Client")
                .rateLimitRpm(2) // limit to 2 rpm
                .activo(true)
                .build();

        when(apiKeyService.validateKey("cs_live_throttled123")).thenReturn(Optional.of(apiKey));

        // First 2 requests succeed
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/external/catalogo");
            req.addHeader("X-API-KEY", "cs_live_throttled123");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // 3rd request must be throttled with 429
        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/api/external/catalogo");
        req3.addHeader("X-API-KEY", "cs_live_throttled123");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilterInternal(req3, res3, filterChain);

        assertEquals(429, res3.getStatus());
        assertEquals("60", res3.getHeader("Retry-After"));
        assertTrue(res3.getContentAsString().contains("Límite de peticiones excedido"));
    }
}
