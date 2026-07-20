package com.centralizesys.service;

import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.gastos.GastoCaja;
import com.centralizesys.model.gastos.GastoCajaAnulacionRequest;
import com.centralizesys.model.gastos.GastoCajaRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.repository.GastoCajaRepository;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GastoCajaServiceTest {

    @Mock
    private GastoCajaRepository gastoCajaRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private GastoCajaService gastoCajaService;

    // -----------------------------------------------------------------------
    // crearGasto
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("crearGasto: Successfully creates an expense and defaults persona to user name if not provided")
    void crearGasto_Success_DefaultsPersonaToUserName() {
        // Arrange
        Long authUserId = 10L;
        GastoCajaRequest request = new GastoCajaRequest();
        request.setMonto(150.5);
        request.setMotivo("Luz");
        request.setCategoria("Servicios");

        Usuario mockUser = new Usuario();
        mockUser.setNombre("Admin Pedro");

        when(usuarioRepository.findById(authUserId)).thenReturn(Optional.of(mockUser));
        when(gastoCajaRepository.save(any(GastoCaja.class))).thenReturn(5L);

        // Act
        Long resultId = gastoCajaService.crearGasto(request, authUserId);

        // Assert
        assertEquals(5L, resultId);

        ArgumentCaptor<GastoCaja> captor = ArgumentCaptor.forClass(GastoCaja.class);
        verify(gastoCajaRepository).save(captor.capture());

        GastoCaja savedGasto = captor.getValue();
        assertEquals(150.5, savedGasto.getMonto());
        assertEquals("Luz", savedGasto.getMotivo());
        assertEquals("Servicios", savedGasto.getCategoria());
        assertEquals("Admin Pedro", savedGasto.getPersonaInvolucrada());
        assertNotNull(savedGasto.getFechaGasto());
        assertNotNull(savedGasto.getFechaRegistro());
        assertEquals(10L, savedGasto.getRegistradoPorUsuarioId());

        verify(auditoriaService).registrarAccion(eq(10L), eq("CREAR_GASTO"), contains("monto: $150.5"));
    }

    @Test
    @DisplayName("crearGasto: Successfully creates an expense with explicit persona and explicit date")
    void crearGasto_Success_ExplicitPersonaAndDate() {
        // Arrange
        Long authUserId = 10L;
        LocalDateTime explicitDate = LocalDateTime.of(2025, Month.JANUARY, 1, 10, 0);

        GastoCajaRequest request = new GastoCajaRequest();
        request.setMonto(500.0);
        request.setMotivo("Retiro");
        request.setCategoria("Retiro Dueño");
        request.setFechaGasto(explicitDate);
        request.setPersonaInvolucrada("Juan Perez");

        when(gastoCajaRepository.save(any(GastoCaja.class))).thenReturn(6L);

        // Act
        gastoCajaService.crearGasto(request, authUserId);

        // Assert
        ArgumentCaptor<GastoCaja> captor = ArgumentCaptor.forClass(GastoCaja.class);
        verify(gastoCajaRepository).save(captor.capture());

        GastoCaja savedGasto = captor.getValue();
        assertEquals("Juan Perez", savedGasto.getPersonaInvolucrada());
        assertEquals(explicitDate, savedGasto.getFechaGasto());

        // Ensure user fetch wasn't called because persona was provided
        verify(usuarioRepository, never()).findById(anyLong());
    }

    // -----------------------------------------------------------------------
    // anularGasto
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("anularGasto: Successfully voids an expense with reason")
    void anularGasto_Success_WithReason() {
        // Arrange
        Long gastoId = 1L;
        Long authUserId = 2L;

        GastoCaja existing = new GastoCaja();
        existing.setId(gastoId);
        existing.setAnulado(false);

        when(gastoCajaRepository.findById(gastoId)).thenReturn(Optional.of(existing));

        GastoCajaAnulacionRequest request = new GastoCajaAnulacionRequest();
        request.setRazonAnulacion("Me equivoqué");

        // Act
        gastoCajaService.anularGasto(gastoId, request, authUserId);

        // Assert
        verify(gastoCajaRepository).anular(gastoId, "Me equivoqué");
        verify(auditoriaService).registrarAccion(eq(2L), eq("ANULAR_GASTO"), contains("Me equivoqué"));
    }

    @Test
    @DisplayName("anularGasto: Successfully voids an expense without reason, adds WARNING prefix to audit")
    void anularGasto_Success_WithoutReason() {
        // Arrange
        Long gastoId = 1L;
        Long authUserId = 2L;

        GastoCaja existing = new GastoCaja();
        existing.setId(gastoId);
        existing.setAnulado(false);

        when(gastoCajaRepository.findById(gastoId)).thenReturn(Optional.of(existing));

        GastoCajaAnulacionRequest request = new GastoCajaAnulacionRequest();
        request.setRazonAnulacion("   "); // blank reason

        // Act
        gastoCajaService.anularGasto(gastoId, request, authUserId);

        // Assert
        verify(gastoCajaRepository).anular(gastoId, ""); // trimmed to empty
        verify(auditoriaService).registrarAccion(eq(2L), eq("ANULAR_GASTO"), startsWith("[WARNING: Sin Razón Especificada]"));
    }

    @Test
    @DisplayName("anularGasto: Throws exception if expense not found")
    void anularGasto_Throws_WhenNotFound() {
        when(gastoCajaRepository.findById(99L)).thenReturn(Optional.empty());

        GastoCajaAnulacionRequest req = new GastoCajaAnulacionRequest();
        assertThrows(ResourceNotFoundException.class,
                () -> gastoCajaService.anularGasto(99L, req, 1L));
    }

    @Test
    @DisplayName("anularGasto: Throws IllegalStateException if expense already voided")
    void anularGasto_Throws_WhenAlreadyVoided() {
        GastoCaja existing = new GastoCaja();
        existing.setId(1L);
        existing.setAnulado(true);

        when(gastoCajaRepository.findById(1L)).thenReturn(Optional.of(existing));

        GastoCajaAnulacionRequest req = new GastoCajaAnulacionRequest();
        assertThrows(IllegalStateException.class,
                () -> gastoCajaService.anularGasto(1L, req, 1L));
    }

    // -----------------------------------------------------------------------
    // obtenerGastos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("obtenerGastos: Returns correct PageResponse with pagination metadata")
    void obtenerGastos_ReturnsCorrectPageResponse() {
        // Arrange
        GastoCaja g1 = new GastoCaja(); g1.setId(1L); g1.setMonto(100.0);
        GastoCaja g2 = new GastoCaja(); g2.setId(2L); g2.setMonto(200.0);

        when(gastoCajaRepository.countAll(2026, 10, null)).thenReturn(2L);
        when(gastoCajaRepository.findAll(15L, 0L, 2026, 10, null)).thenReturn(List.of(g1, g2));

        // Act
        PageResponse<GastoCaja> response = gastoCajaService.obtenerGastos(0L, 15L, 2026, 10, null);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertEquals(0L, response.page());
        assertEquals(15L, response.size());
        assertEquals(2L, response.totalElements());
        assertEquals(1L, response.totalPages()); // ceil(2/15) = 1
    }

    @Test
    @DisplayName("obtenerGastos: Returns empty PageResponse when no gastos match the filter")
    void obtenerGastos_EmptyResult() {
        // Arrange
        when(gastoCajaRepository.countAll(null, null, null)).thenReturn(0L);
        when(gastoCajaRepository.findAll(15L, 0L, null, null, null)).thenReturn(List.of());

        // Act
        PageResponse<GastoCaja> response = gastoCajaService.obtenerGastos(0L, 15L, null, null, null);

        // Assert
        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(0L, response.totalElements());
        assertEquals(0L, response.totalPages()); // ceil(0/15) = 0
    }

    @Test
    @DisplayName("obtenerGastos: Correctly calculates totalPages for partial final page")
    void obtenerGastos_CalculatesTotalPages_Partial() {
        // Arrange: 16 items with page size 15 → 2 pages
        when(gastoCajaRepository.countAll(null, null, null)).thenReturn(16L);
        when(gastoCajaRepository.findAll(15L, 15L, null, null, null)).thenReturn(List.of(new GastoCaja()));

        // Act
        PageResponse<GastoCaja> response = gastoCajaService.obtenerGastos(1L, 15L, null, null, null);

        // Assert: page=1, offset=15, totalPages=2
        assertEquals(1L, response.page());
        assertEquals(2L, response.totalPages());
    }
}
