package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import org.junit.jupiter.api.Test;

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
    void deleteById_ShouldRemoveUser() {
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

        // Assert
        Optional<Usuario> check = usuarioRepository.findById(id);
        assertThat(check).isEmpty();
    }
}
