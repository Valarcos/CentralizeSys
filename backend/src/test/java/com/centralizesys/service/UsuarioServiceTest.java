package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    // --- LOGIN TESTS ---

    @Test
    @DisplayName("Login Success: Returns user and audits action")
    void login_Success() {
        // Arrange
        Usuario user = new Usuario(1L, "Admin", "admin@test.com", "hashed123", UsuarioRole.ADMIN, "date");
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw123", "hashed123")).thenReturn(true);

        // Act
        Usuario result = usuarioService.login("admin@test.com", "raw123");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(auditoriaService).registrarAccion(1L, "LOGIN", "Inicio de sesión exitoso.");
    }

    @Test
    @DisplayName("Login Failure: Email not found throws Friendly Exception")
    void login_EmailNotFound() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login("unknown@test.com", "pass"));

        assertEquals("El correo electrónico no existe en el sistema.", ex.getMessage());
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Login Failure: Wrong Password throws Friendly Exception")
    void login_WrongPassword() {
        Usuario user = new Usuario(1L, "Admin", "admin@test.com", "hashed123", UsuarioRole.ADMIN, "date");
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed123")).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login("admin@test.com", "wrong"));

        assertEquals("La contraseña es incorrecta.", ex.getMessage());
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    // --- REGISTRATION TESTS ---

    @Test
    @DisplayName("Register Success: Hashes password and saves")
    void register_Success() {
        when(usuarioRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawPass")).thenReturn("hashedPass");

        usuarioService.registrarUsuario("New User", "new@test.com", "rawPass");

        verify(usuarioRepository).save(argThat(u -> u.getEmail().equals("new@test.com") &&
                u.getPasswordHash().equals("hashedPass") &&
                u.getNombre().equals("New User")));
    }

    @Test
    @DisplayName("Register Failure: Duplicate Email throws Exception")
    void register_DuplicateEmail() {
        when(usuarioRepository.findByEmail("prop@test.com")).thenReturn(Optional.of(new Usuario()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarUsuario("User", "prop@test.com", "pass"));

        assertTrue(ex.getMessage().contains("ya está registrado"));
        verify(usuarioRepository, never()).save(any());
    }
}
