package com.centralizesys.controller;

import com.centralizesys.config.DataPathConfig;
import com.centralizesys.util.DatabaseRestorer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemStatusController.class)
class SystemStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataPathConfig dataPathConfig; // Not strictly used since we mock static, but good practice in WebMvcTest

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService; // Required by GlobalExceptionHandler

    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider; // Required by JwtAuthenticationFilter

    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService; // Required by
    // JwtAuthenticationFilter

    @MockBean
    private com.centralizesys.service.ActiveTokenCacheService activeTokenCacheService; // Required by JwtAuthenticationFilter

    @MockBean
    private com.centralizesys.repository.ActiveTokenRepository activeTokenRepository; // Required by JwtAuthenticationFilter

    @TempDir
    Path tempDir;

    private MockedStatic<DataPathConfig> dataPathConfigMock;
    private Path flagFile;

    @BeforeEach
    void setUp() {
        // Mock static DataPathConfig to point to our temp file
        dataPathConfigMock = Mockito.mockStatic(DataPathConfig.class);
        flagFile = tempDir.resolve("restore_failed.flag");

        dataPathConfigMock.when(() -> DataPathConfig.resolve(DatabaseRestorer.RESTORE_FAILED_FLAG))
                .thenReturn(flagFile);
    }

    @AfterEach
    void tearDown() {
        dataPathConfigMock.close();
    }

    @Test
    @WithMockUser
    void whenFlagExists_UTF8_shouldReturnAlertAndConsumeFlag() throws Exception {
        // GIVEN
        String content = "RESTORE_SKIPPED|size=100|timestamp=20240101";
        Files.writeString(flagFile, content, StandardCharsets.UTF_8);

        // WHEN
        mockMvc.perform(get("/api/system/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].type").value("RESTORE_FAILED"))
                .andExpect(jsonPath("$.alerts[0].details").value(content));

        // THEN
        assertFalse(Files.exists(flagFile), "Flag file should be deleted after reading");
    }

    @Test
    @WithMockUser
    void whenFlagExists_UTF16_shouldReturnAlertAndConsumeFlag() throws Exception {
        // GIVEN - Write as UTF-16 (Simulating PowerShell '>')
        String content = "RESTORE_SKIPPED|size=200|encoding=UTF16";
        Files.writeString(flagFile, content, StandardCharsets.UTF_16);

        // WHEN
        mockMvc.perform(get("/api/system/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].type").value("RESTORE_FAILED"))
                .andExpect(jsonPath("$.alerts[0].details", containsString("RESTORE_SKIPPED")));

        // THEN
        assertFalse(Files.exists(flagFile), "Flag file should be deleted after reading");
    }

    @Test
    @WithMockUser
    void whenFlagExists_Garbage_shouldReturnFallbackAndConsumeFlag() throws Exception {
        // GIVEN - Write random bytes that are invalid in UTF-8
        byte[] garbage = new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0xFD }; // random binary
        Files.write(flagFile, garbage);

        // WHEN
        mockMvc.perform(get("/api/system/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].type").value("RESTORE_FAILED"));
        // We expect it to succeed without 500. content might be garbled but treated as
        // ISO-8859-1.

        // THEN
        assertFalse(Files.exists(flagFile), "Flag file should be deleted even if content is garbage");
    }

    @Test
    @WithMockUser
    void whenNoFlag_shouldReturnEmptyList() throws Exception {
        // GIVEN - no file

        // WHEN
        mockMvc.perform(get("/api/system/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(0)));
    }
}
