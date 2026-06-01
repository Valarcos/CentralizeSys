package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.product.Location;
import com.centralizesys.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private StockRepository repository;

    @InjectMocks
    private LocationService service;

    @Test
    @DisplayName("Create Success: Adds valid numeric location")
    void create_Success() {
        String validName = "1";
        when(repository.createLocation(validName)).thenReturn(10L);

        Location result = service.create(validName);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("1", result.getNombre());
        verify(repository).createLocation(validName);
    }

    @Test
    @DisplayName("Create Fail: Throws on null or empty name")
    void create_NullOrEmpty() {
        assertThrows(BusinessRuleException.class, () -> service.create(null));
        assertThrows(BusinessRuleException.class, () -> service.create(""));
        assertThrows(BusinessRuleException.class, () -> service.create("   "));

        verify(repository, never()).createLocation(anyString());
    }

    @Test
    @DisplayName("Create Fail: Throws on non-numeric name")
    void create_NonNumeric() {
        // "A" is not a number
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.create("A"));
        assertTrue(ex.getMessage().contains("debe ser un número"));

        // "1-1" is not a pure number
        assertThrows(BusinessRuleException.class, () -> service.create("1-1"));

        verify(repository, never()).createLocation(anyString());
    }

    @Test
    @DisplayName("Create Fail: Handles DuplicateKeyException friendly")
    void create_Duplicate() {
        String duplicateInfo = "5";
        when(repository.createLocation(duplicateInfo)).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                        "violates unique constraint: ubicaciones.nombre"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.create(duplicateInfo));

        // Assert friendly message translation
        assertTrue(ex.getMessage().contains("estantería número '5' ya existe"));
    }
}
