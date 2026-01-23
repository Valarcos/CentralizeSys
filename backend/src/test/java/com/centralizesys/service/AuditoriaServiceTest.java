package com.centralizesys.service;

import com.centralizesys.repository.AuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository repository;

    @InjectMocks
    private AuditoriaService service;

    @Test
    @DisplayName("Success: registrarAccion calls repository")
    void registrarAccion_Success() {
        service.registrarAccion(1L, "TEST", "Details");

        verify(repository).save(1L, "TEST", "Details");
    }

    @Test
    @DisplayName("Fail-Safe: registrarAccion swallows exceptions (Log only)")
    void registrarAccion_SwallowsException() {
        // Arrange: Repository throws a RuntimeException (simulating DB down)
        doThrow(new RuntimeException("DB Connection Failed"))
                .when(repository).save(anyLong(), anyString(), anyString());

        // Act & Assert
        // This ensures the method completes normally even if the DB fails.
        // If the 'try-catch' wasn't there, this would fail.
        assertDoesNotThrow(() -> service.registrarAccion(1L, "TEST", "Details"));

        // Verify it TRIED to save
        verify(repository).save(1L, "TEST", "Details");
    }
}
