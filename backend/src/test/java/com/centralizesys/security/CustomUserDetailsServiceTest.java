package com.centralizesys.security;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
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
    @DisplayName("loadUserByUsername returns UserDetails for an active user")
    void loadUserByUsername_Success() {
        // Arrange
        Usuario user = new Usuario(1L, "Test User", "test@test.com", "hashedPass", UsuarioRole.EMPLEADO, java.time.LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

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
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user does not exist")
    void loadUserByUsername_NotFound() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername("unknown@test.com");
        });
    }

    @Test
    @DisplayName("loadUserByUsername throws DisabledException when user is logically deleted (activo=false)")
    void loadUserByUsername_InactiveUser_ThrowsDisabledException() {
        // Arrange: Simulate a user object returned with activo=false.
        // This covers the defence-in-depth check inside the service, independently
        // of the repository's activo=true filter.
        Usuario inactiveUser = new Usuario(5L, "Deleted User", "deleted@test.com", "hash",
                UsuarioRole.EMPLEADO, java.time.LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), false);

        when(usuarioRepository.findByEmail("deleted@test.com")).thenReturn(Optional.of(inactiveUser));

        // Act & Assert
        DisabledException ex = assertThrows(DisabledException.class,
                () -> customUserDetailsService.loadUserByUsername("deleted@test.com"));

        assertTrue(ex.getMessage().contains("deleted@test.com"),
                "Exception message must identify the disabled account email");
    }
}
