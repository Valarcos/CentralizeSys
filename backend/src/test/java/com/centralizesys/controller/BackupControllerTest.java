package com.centralizesys.controller;

import com.centralizesys.security.CustomUserDetails;
import com.centralizesys.service.BackupService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BackupController.class)
@AutoConfigureMockMvc(addFilters = false)
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    // Security Mocks
    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;
    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;
    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("restoreDatabase uses SecurityContext ID")
    void restoreDatabase_UsesSecurityContext() throws Exception {
        Long userId = 33L;
        String filename = "backup.sql";

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(userId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        try {
            // Act
            // Path updated to /api/backups
            mockMvc.perform(post("/api/backups/restore/" + filename)
                            .param("confirm", "true"))
                    .andExpect(status().isOk());

            // Assert
            verify(backupService).scheduleRestore(filename, userId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("triggerManualBackup uses SecurityContext ID")
    void triggerManualBackup_UsesSecurityContext() throws Exception {
        Long userId = 42L;

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(userId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        try {
            // Act
            // Path updated to /api/backups/create (POST)
            mockMvc.perform(post("/api/backups/create"))
                    .andExpect(status().isOk());

            // Assert
            verify(backupService).performBackup(BackupService.BackupType.MANUAL, userId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
