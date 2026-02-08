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

    // --- UPDATE TESTS ---

    @Test
    @DisplayName("Update Success: Updates all provided fields")
    void update_Success_AllFields() {
        // Arrange
        Usuario existing = new Usuario(1L, "Old Name", "old@test.com", "oldHash", UsuarioRole.EMPLEADO, "date");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("newPassword")).thenReturn("newHash");

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "New Name", "new@test.com", "newPassword", "ADMIN");

        try (var mockedSecurityUtils = mockStatic(com.centralizesys.security.SecurityUtils.class)) {
            mockedSecurityUtils.when(com.centralizesys.security.SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            // Act
            usuarioService.update(1L, request);

            // Assert
            verify(usuarioRepository).update(argThat(u -> u.getNombre().equals("New Name") &&
                    u.getEmail().equals("new@test.com") &&
                    u.getPasswordHash().equals("newHash") &&
                    u.getRol() == UsuarioRole.ADMIN));
            verify(auditoriaService).registrarAccion(99L, "UPDATE_USER", "Usuario actualizado: new@test.com");
        }
    }

    @Test
    @DisplayName("Update Failure: Cannot modify system user (id=0)")
    void update_CannotModifySystemUser() {
        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "Name", null, null, null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(0L, request));

        assertEquals("No se puede modificar al Usuario del Sistema.", ex.getMessage());
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: User not found throws ResourceNotFoundException")
    void update_UserNotFound() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "Name", null, null, null);

        assertThrows(com.centralizesys.exception.ResourceNotFoundException.class,
                () -> usuarioService.update(999L, request));
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: Duplicate email throws Exception")
    void update_DuplicateEmail() {
        Usuario existing = new Usuario(1L, "Name", "old@test.com", "hash", UsuarioRole.EMPLEADO, "date");
        Usuario other = new Usuario(2L, "Other", "taken@test.com", "hash", UsuarioRole.EMPLEADO, "date");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(other));

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                null, "taken@test.com", null, null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(1L, request));

        assertTrue(ex.getMessage().contains("ya está registrado"));
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: Invalid role throws Exception")
    void update_InvalidRole() {
        Usuario existing = new Usuario(1L, "Name", "test@test.com", "hash", UsuarioRole.EMPLEADO, "date");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                null, null, null, "INVALID_ROLE");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(1L, request));

        assertEquals("Rol debe ser ADMIN o EMPLEADO.", ex.getMessage());
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Success: Same email does not trigger duplicate check")
    void update_SameEmail_NoConflict() {
        Usuario existing = new Usuario(1L, "Name", "same@test.com", "hash", UsuarioRole.EMPLEADO, "date");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                null, "same@test.com", null, null);

        try (var mockedSecurityUtils = mockStatic(com.centralizesys.security.SecurityUtils.class)) {
            mockedSecurityUtils.when(com.centralizesys.security.SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            usuarioService.update(1L, request);

            // findByEmail should NOT be called when email is unchanged
            verify(usuarioRepository, never()).findByEmail(anyString());
            verify(usuarioRepository).update(existing);
        }
    }

    @Test
    @DisplayName("Update Success: Null/blank fields are ignored")
    void update_NullFields_Ignored() {
        Usuario existing = new Usuario(1L, "Original", "orig@test.com", "origHash", UsuarioRole.EMPLEADO, "date");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                null, "", "   ", null);

        try (var mockedSecurityUtils = mockStatic(com.centralizesys.security.SecurityUtils.class)) {
            mockedSecurityUtils.when(com.centralizesys.security.SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            usuarioService.update(1L, request);

            // Verify original values unchanged
            verify(usuarioRepository).update(argThat(u -> u.getNombre().equals("Original") &&
                    u.getEmail().equals("orig@test.com") &&
                    u.getPasswordHash().equals("origHash") &&
                    u.getRol() == UsuarioRole.EMPLEADO));
        }
    }
}
