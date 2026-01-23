package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.auth.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UsuarioService usuarioService;

    @Test
    @DisplayName("IT-01: Registrar Usuario persists to DB and hashes password")
    void testRegistrarUsuario_Persistence() {
        // Act
        usuarioService.registrarUsuario("Integration User", "it@test.com", "pass123");

        // Assert (Direct DB Verification)
        Usuario saved = usuarioRepository.findByEmail("it@test.com")
                .orElseThrow(() -> new AssertionError("User not saved to DB"));

        assertEquals("Integration User", saved.getNombre());
        assertNotEquals("pass123", saved.getPasswordHash()); // Must be hashed
        assertTrue(passwordEncoder.matches("pass123", saved.getPasswordHash()));
    }

    @Test
    @DisplayName("IT-02: Registrar Usuario enforces DB duplicate check (Safety Net)")
    void testRegistrarUsuario_Duplicate_Logic() {
        // Arrange: Pre-populate directly via Repository
        Usuario u = new Usuario();
        u.setEmail("dup@test.com");
        u.setNombre("Existing");
        u.setPasswordHash("hash");
        usuarioRepository.save(u);

        // Act & Assert
        // The Service check (logic) should catch it first.
        // If we were testing ONLY the repo, we'd expect
        // DataIntegrityViolationException.
        // But here we test the flow.
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarUsuario("New", "dup@test.com", "pass"));

        assertTrue(ex.getMessage().contains("ya está registrado"));
    }

    @Test
    @DisplayName("IT-03: Login finds user in DB and audits")
    void testLogin_Success_And_Audit() {
        // Arrange
        usuarioService.registrarUsuario("Login User", "login@test.com", "secret");
        Long userId = usuarioRepository.findByEmail("login@test.com")
                .orElseThrow(() -> new AssertionError("Setup failed: User not found"))
                .getId();

        // Act
        Usuario loggedIn = usuarioService.login("login@test.com", "secret");

        // Assert
        assertEquals(userId, loggedIn.getId());

        // Assert
        assertEquals(userId, loggedIn.getId());

        // IT-Verification: Ensure Audit was actually written (detect silent failures)
        // Note: AuditoriaService swallows exceptions, so we must check the DB count.
        int auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auditoria WHERE usuario_id = ?",
                Integer.class, userId);
        assertEquals(1, auditCount, "Audit log should be persisted");
    }
}
