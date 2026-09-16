package com.centralizesys.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.centralizesys.util.Constants;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:3000"})
class SecurityCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String ORIGIN_URL = "http://localhost:3000";

    @Test
    void whenUnauthorized401_thenCorsHeadersArePreserved() throws Exception {
        // DEFENSE: Explicitly clear any thread-local security context that might have leaked
        // from other poorly written tests in the suite running on the same JVM thread.
        SecurityContextHolder.clearContext();

        // Attempting to access a protected endpoint WITHOUT a token
        mockMvc.perform(get("/api/productos")
                        .header(HttpHeaders.ORIGIN, ORIGIN_URL)
                        .accept(MediaType.APPLICATION_JSON))

                // Assert it returns 401
                .andExpect(status().isUnauthorized())

                // Assert our custom JSON format is returned
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(Constants.ERR_UNAUTHORIZED))

                // CRITICAL ASSERTION: Prove that CORS headers survived the rejection
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN_URL));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO") // Simulate a logged-in employee
    void whenForbidden403_thenCorsHeadersArePreserved() throws Exception {
        // Attempting to access an Admin-only endpoint
        // NOTE: Make sure /api/backups/restore/dummy is protected with @PreAuthorize("hasRole('ADMIN')")
        mockMvc.perform(MockMvcRequestBuilders.post("/api/backups/restore/dummy")
                        .header(HttpHeaders.ORIGIN, ORIGIN_URL)
                        .accept(MediaType.APPLICATION_JSON))

                // Assert it returns 403
                .andExpect(status().isForbidden())

                // Assert our custom JSON format is returned
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(Constants.ERR_ACCESS_DENIED))

                // CRITICAL ASSERTION: Prove that CORS headers survived the rejection
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN_URL));
    }
}
