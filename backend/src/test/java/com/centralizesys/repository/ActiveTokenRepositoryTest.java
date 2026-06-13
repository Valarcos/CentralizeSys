package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.ActiveToken;
import com.centralizesys.model.auth.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveTokenRepositoryTest extends BaseIntegrationTest {

    // Fixed reference date to comply with Sonar S5977 "Do not use the system clock in tests"
    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, Month.JANUARY, 15, 10, 0, 0);

    @Autowired
    private ActiveTokenRepository activeTokenRepository;

    @Test
    @DisplayName("save - persists a new active token")
    void save_persistsNewToken() {
        Long userId = createTestUser();
        String jti = UUID.randomUUID().toString();
        LocalDateTime expiresAt = FIXED_NOW.plusDays(7);

        activeTokenRepository.save(userId, jti, expiresAt);

        Optional<ActiveToken> result = activeTokenRepository.findByJti(jti);
        assertThat(result).isPresent();
        assertThat(result.get().getUsuarioId()).isEqualTo(userId);
        assertThat(result.get().getJti()).isEqualTo(jti);
        assertThat(result.get().getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("findByJti - returns empty for unknown JTI")
    void findByJti_returnsEmpty_forUnknownJti() {
        Optional<ActiveToken> result = activeTokenRepository.findByJti("no-such-jti");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByUsuarioId - removes all tokens for a given user (session enforcement)")
    void deleteByUsuarioId_removesAllTokensForUser() {
        Long userId = createTestUser();
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();

        activeTokenRepository.save(userId, jti1, FIXED_NOW.plusDays(7));
        activeTokenRepository.save(userId, jti2, FIXED_NOW.plusDays(7));

        // Confirm both tokens exist
        assertThat(activeTokenRepository.findByJti(jti1)).isPresent();
        assertThat(activeTokenRepository.findByJti(jti2)).isPresent();

        // Act: simulate new login — invalidate all prior sessions
        activeTokenRepository.deleteByUsuarioId(userId);

        // Assert: both tokens are gone
        assertThat(activeTokenRepository.findByJti(jti1)).isEmpty();
        assertThat(activeTokenRepository.findByJti(jti2)).isEmpty();
    }

    @Test
    @DisplayName("deleteByUsuarioId - does not affect tokens of other users")
    void deleteByUsuarioId_doesNotAffectOtherUsers() {
        Long userA = createTestUser();
        Long userB = createSecondTestUser();
        String jtiA = UUID.randomUUID().toString();
        String jtiB = UUID.randomUUID().toString();

        activeTokenRepository.save(userA, jtiA, FIXED_NOW.plusDays(7));
        activeTokenRepository.save(userB, jtiB, FIXED_NOW.plusDays(7));

        // Invalidate only userA
        activeTokenRepository.deleteByUsuarioId(userA);

        // User B's token must survive
        assertThat(activeTokenRepository.findByJti(jtiA)).isEmpty();
        assertThat(activeTokenRepository.findByJti(jtiB)).isPresent();
    }

    @Test
    @DisplayName("deleteByJti - removes exactly one token (logout)")
    void deleteByJti_removesExactlyOneToken() {
        Long userId = createTestUser();
        String jtiToDelete = UUID.randomUUID().toString();
        String jtiToKeep   = UUID.randomUUID().toString();

        activeTokenRepository.save(userId, jtiToDelete, FIXED_NOW.plusDays(7));
        activeTokenRepository.save(userId, jtiToKeep, FIXED_NOW.plusDays(7));

        activeTokenRepository.deleteByJti(jtiToDelete);

        assertThat(activeTokenRepository.findByJti(jtiToDelete)).isEmpty();
        assertThat(activeTokenRepository.findByJti(jtiToKeep)).isPresent();
    }

    @Test
    @DisplayName("findAllValid - returns only non-expired tokens")
    void findAllValid_returnsOnlyNonExpiredTokens() {
        Long userId = createTestUser();
        String validJti   = UUID.randomUUID().toString();
        String expiredJti = UUID.randomUUID().toString();

        activeTokenRepository.save(userId, validJti,   FIXED_NOW.plusDays(7));
        activeTokenRepository.save(userId, expiredJti, FIXED_NOW.minusDays(1)); // already expired

        List<ActiveToken> valid = activeTokenRepository.findAllValid(FIXED_NOW);

        assertThat(valid).extracting(ActiveToken::getJti).contains(validJti);
        assertThat(valid).extracting(ActiveToken::getJti).doesNotContain(expiredJti);
    }

    @Test
    @DisplayName("deleteExpired - removes only expired tokens")
    void deleteExpired_removesOnlyExpiredTokens() {
        Long userId = createTestUser();
        String validJti   = UUID.randomUUID().toString();
        String expiredJti = UUID.randomUUID().toString();

        activeTokenRepository.save(userId, validJti,   FIXED_NOW.plusDays(7));
        activeTokenRepository.save(userId, expiredJti, FIXED_NOW.minusDays(1));

        activeTokenRepository.deleteExpired(FIXED_NOW);

        assertThat(activeTokenRepository.findByJti(validJti)).isPresent();
        assertThat(activeTokenRepository.findByJti(expiredJti)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helper: creates a second test user with a different email
    // -------------------------------------------------------------------------
    private Long createSecondTestUser() {
        return usuarioRepository.findByEmail("test2@admin.com")
                .map(Usuario::getId)
                .orElseGet(() -> {
                    com.centralizesys.model.auth.Usuario u = new com.centralizesys.model.auth.Usuario();
                    u.setNombre("Test Admin 2");
                    u.setEmail("test2@admin.com");
                    u.setPasswordHash(passwordEncoder.encode("123456"));
                    u.setRol(com.centralizesys.model.auth.UsuarioRole.EMPLEADO);
                    usuarioRepository.save(u);
                    return usuarioRepository.findByEmail("test2@admin.com")
                            .orElseThrow(() -> new RuntimeException("User 2 creation failed"))
                            .getId();
                });
    }
}
