package com.centralizesys.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class WebMvcConfigTest {

    private static final String EXPECTED_CACHE_CONTROL = "no-store, must-revalidate";

    @RestController
    static class StubController {
        @GetMapping("/api/stub")
        ResponseEntity<String> api() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/auth/stub")
        ResponseEntity<String> auth() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/stub-error")
        ResponseEntity<String> apiError() { return ResponseEntity.badRequest().body("bad"); }

        @GetMapping("/public/stub")
        ResponseEntity<String> outside() { return ResponseEntity.ok("ok"); }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addInterceptors(WebMvcConfig.buildApiCacheControlInterceptor())
                .build();
    }

    @Test
    void apiResponsesAreNoStore() throws Exception {
        mockMvc.perform(get("/api/stub"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", EXPECTED_CACHE_CONTROL));
    }

    @Test
    void nestedApiPathsAreNoStore() throws Exception {
        mockMvc.perform(get("/api/auth/stub"))
                .andExpect(header().string("Cache-Control", EXPECTED_CACHE_CONTROL));
    }

    @Test
    void apiErrorResponsesAreNoStore() throws Exception {
        mockMvc.perform(get("/api/stub-error"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", EXPECTED_CACHE_CONTROL));
    }

    @Test
    void nonApiPathsAreNotTouched() throws Exception {
        String cacheControl = mockMvc.perform(get("/public/stub"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");
        assertFalse(EXPECTED_CACHE_CONTROL.equals(cacheControl));
        assertNull(cacheControl);
    }
}
