package com.centralizesys.controller;

import com.centralizesys.security.CustomUserDetails;
import com.centralizesys.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("DELETE /api/productos/{id} uses SecurityContext ID")
    void delete_UsesSecurityContextId() throws Exception {
        Long productId = 123L;
        Long userId = 99L;

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(userId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        try {
            // Act: Perform delete WITHOUT usuarioId param
            mockMvc.perform(delete("/api/productos/" + productId))
                    .andExpect(status().isNoContent());

            // Assert: Service called with correct User ID
            verify(productService).deleteById(productId, userId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
