package com.centralizesys.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.centralizesys.model.dashboard.DashboardSummaryResponse;
import com.centralizesys.model.dashboard.SystemAlertDTO;
import com.centralizesys.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
    }

    @Test
    void getSummaryReturnsOkWithExpectedPayload() throws Exception {
        SystemAlertDTO alert = new SystemAlertDTO("Backup disk space low", "WARNING", "BACKUP");
        DashboardSummaryResponse response = new DashboardSummaryResponse(
                Collections.emptyList(),
                null,
                true,
                Collections.emptyList(),
                List.of(alert)
        );

        when(dashboardService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveDebts").value(true))
                .andExpect(jsonPath("$.stockAlerts").isArray())
                .andExpect(jsonPath("$.pendingCheques").isArray())
                .andExpect(jsonPath("$.systemAlerts").isArray())
                .andExpect(jsonPath("$.systemAlerts[0].message").value("Backup disk space low"))
                .andExpect(jsonPath("$.systemAlerts[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.systemAlerts[0].source").value("BACKUP"));
    }
}
