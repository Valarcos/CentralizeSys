package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioRepositoryTest extends BaseIntegrationTest {

    @Test
    void findAll_ShouldReturnAllUsers() {
        // Arrange
        // (BaseIntegrationTest setup might have some users, but let's add fresh ones)
        // Clean table first if needed, but BaseIntegrationTest preserves users.
        // Let's count existing first.
        int initialCount = usuarioRepository.findAll().size();

        Usuario u1 = new Usuario();
        u1.setNombre("User A");
        u1.setEmail("a@test.com");
        u1.setPasswordHash("hash");
        u1.setRol(UsuarioRole.EMPLEADO);
        usuarioRepository.save(u1);

        Usuario u2 = new Usuario();
        u2.setNombre("User B");
        u2.setEmail("b@test.com");
        u2.setPasswordHash("hash");
        u2.setRol(UsuarioRole.ADMIN);
        usuarioRepository.save(u2);

        // Act
        List<Usuario> results = usuarioRepository.findAll();

        // Assert
        assertThat(results).hasSize(initialCount + 2);
        assertThat(results)
                .extracting(Usuario::getEmail)
                .contains("a@test.com", "b@test.com");
    }

    @Test
    void softDelete_SetsActivoFalseAndHidesUserFromActiveQueries() {
        // Arrange
        Usuario u = new Usuario();
        u.setNombre("To Delete");
        u.setEmail("del@test.com");
        u.setPasswordHash("hash");
        u.setRol(UsuarioRole.EMPLEADO);
        usuarioRepository.save(u);

        // Retrieve ID
        Long id = usuarioRepository.findByEmail("del@test.com")
                .orElseThrow(() -> new AssertionError("User not found for deletion setup"))
                .getId();

        // Act
        usuarioRepository.deleteById(id);

        // Assert 1: User no longer visible via active-filtered query
        Optional<Usuario> check = usuarioRepository.findById(id);
        assertThat(check).isEmpty();

        // Assert 2: Physical row still exists with activo = false,
        // proving this is a true soft-delete and not a physical row removal.
        Boolean activo = jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE id = ?",
                Boolean.class, id);
        assertThat(activo).isFalse();
    }

    @Test
    @DisplayName("Partial Unique Index allows re-registering email if previous user is soft-deleted")
    void partialUniqueIndex_AllowsReuseOfEmailIfDeleted() {
        // 1. Create a user and soft delete it
        Usuario u1 = new Usuario();
        u1.setNombre("User 1");
        u1.setEmail("reuse@test.com");
        u1.setPasswordHash("hash1");
        u1.setRol(UsuarioRole.EMPLEADO);
        usuarioRepository.save(u1);

        Usuario saved1 = usuarioRepository.findByEmail("reuse@test.com")
                .orElseThrow(() -> new AssertionError("User not found after save"));

        usuarioRepository.deleteById(saved1.getId());

        // 2. Create another user with the exact same email
        Usuario u2 = new Usuario();
        u2.setNombre("User 2");
        u2.setEmail("reuse@test.com");
        u2.setPasswordHash("hash2");
        u2.setRol(UsuarioRole.ADMIN);

        // 3. Save should succeed without DataIntegrityViolationException
        usuarioRepository.save(u2);

        Usuario saved2 = usuarioRepository.findByEmail("reuse@test.com")
                .orElseThrow(() -> new AssertionError("User not found after second save"));

        assertThat(saved2.getId()).isNotNull();
        assertThat(saved2.getId()).isNotEqualTo(saved1.getId());
    }
}
