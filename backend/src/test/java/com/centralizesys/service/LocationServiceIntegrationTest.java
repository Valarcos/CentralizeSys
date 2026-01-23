package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.product.Location;
import com.centralizesys.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class LocationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private StockRepository stockRepository;

    @Test
    @DisplayName("IT-01: Create Location persists to DB")
    void create_PersistsToDB() {
        // Act
        Location created = locationService.create("1");

        // Assert
        assertNotNull(created);
        assertEquals("1", created.getNombre());
        assertNotNull(created.getId());

        // Direct DB Verification
        boolean exists = stockRepository.findAllLocations().stream()
                .anyMatch(l -> l.getNombre().equals("1"));
        assertTrue(exists, "Location '1' should exist in DB");
    }

    @Test
    @DisplayName("IT-02: Create Duplicate Location throws BusinessRuleException via DB Constraint")
    void create_Duplicate_ThrowsFriendlyException() {
        // Arrange: Create first one
        locationService.create("5");

        // Act & Assert
        // This confirms that the SERVICE catches the DB's DuplicateKeyException
        // and rethrows it as BusinessRuleException.
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> locationService.create("5"));

        assertTrue(ex.getMessage().contains("estantería número '5' ya existe"));
    }
}
