package com.centralizesys.security;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(usuarioRepository);
    }

    @Test
    void loadUserByUsername_Success() {
        // Arrange
        Usuario user = new Usuario(1L, "Test User", "test@test.com", "hashedPass", UsuarioRole.EMPLEADO, "now");

        when(usuarioRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("test@test.com");

        // Assert
        assertNotNull(userDetails);
        assertEquals("test@test.com", userDetails.getUsername());
        assertEquals("hashedPass", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLEADO")));
    }

    @Test
    void loadUserByUsername_NotFound() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername("unknown@test.com");
        });
    }
}
