package com.centralizesys.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.dashboard.DashboardSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

@DisplayName("DashboardController Integration Tests")
class DashboardControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DashboardController dashboardController;

    @Test
    void testGetSummaryIntegration() {
        Long adminId = createTestUser();
        authenticateUser(adminId, "ROLE_ADMIN");

        ResponseEntity<DashboardSummaryResponse> response = dashboardController.getSummary();

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().stockAlerts());
        assertNotNull(response.getBody().pendingCheques());
    }
}
