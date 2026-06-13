package com.centralizesys.service;

import com.centralizesys.model.auth.LoginAttempt;
import com.centralizesys.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    // Fixed reference date — Sonar S5977: do not use the system clock in tests
    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, Month.JANUARY, 15, 10, 0, 0);

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(loginAttemptRepository, auditoriaService);
    }

    // =========================================================================
    // isBlocked (gray-box helper tests)
    // =========================================================================

    @Test
    @DisplayName("isBlocked - returns true when blocked_until is in the future")
    void isBlocked_returnsTrue_whenBlockedUntilIsInFuture() {
        LoginAttempt attempt = buildAttempt(3, FIXED_NOW.minusMinutes(5), FIXED_NOW.plusMinutes(25));
        assertThat(service.isBlocked(attempt, FIXED_NOW)).isTrue();
    }

    @Test
    @DisplayName("isBlocked - returns false when blocked_until is null")
    void isBlocked_returnsFalse_whenBlockedUntilIsNull() {
        LoginAttempt attempt = buildAttempt(3, FIXED_NOW.minusMinutes(5), null);
        assertThat(service.isBlocked(attempt, FIXED_NOW)).isFalse();
    }

    @Test
    @DisplayName("isBlocked - returns false when blocked_until is in the past")
    void isBlocked_returnsFalse_whenBlockedUntilIsInPast() {
        LoginAttempt attempt = buildAttempt(5, FIXED_NOW.minusHours(1), FIXED_NOW.minusMinutes(1));
        assertThat(service.isBlocked(attempt, FIXED_NOW)).isFalse();
    }

    // =========================================================================
    // isWithinWindow (gray-box helper tests)
    // =========================================================================

    @Test
    @DisplayName("isWithinWindow - returns true when last_attempt is within 15 minutes")
    void isWithinWindow_returnsTrue_whenWithinWindow() {
        LoginAttempt attempt = buildAttempt(2, FIXED_NOW.minusMinutes(10), null);
        assertThat(service.isWithinWindow(attempt, FIXED_NOW)).isTrue();
    }

    @Test
    @DisplayName("isWithinWindow - returns false when last_attempt is older than 15 minutes")
    void isWithinWindow_returnsFalse_whenOutsideWindow() {
        LoginAttempt attempt = buildAttempt(4, FIXED_NOW.minusMinutes(20), null);
        assertThat(service.isWithinWindow(attempt, FIXED_NOW)).isFalse();
    }

    @Test
    @DisplayName("isWithinWindow - returns false when last_attempt is null")
    void isWithinWindow_returnsFalse_whenLastAttemptIsNull() {
        LoginAttempt attempt = buildAttempt(0, null, null);
        assertThat(service.isWithinWindow(attempt, FIXED_NOW)).isFalse();
    }

    // =========================================================================
    // checkAndThrowIfBlocked
    // =========================================================================

    @Test
    @DisplayName("checkAndThrowIfBlocked - throws LockedException for an actively blocked account")
    void checkAndThrowIfBlocked_throwsLockedException_whenBlocked() {
        LoginAttempt blocked = buildAttempt(5, FIXED_NOW.minusMinutes(5), FIXED_NOW.plusMinutes(25));
        when(loginAttemptRepository.findByEmail("victim@test.com")).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.checkAndThrowIfBlocked("victim@test.com", FIXED_NOW))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("checkAndThrowIfBlocked - does not throw for an unblocked account")
    void checkAndThrowIfBlocked_doesNotThrow_whenNotBlocked() {
        when(loginAttemptRepository.findByEmail("ok@test.com")).thenReturn(Optional.empty());
        // No exception expected
        service.checkAndThrowIfBlocked("ok@test.com", FIXED_NOW);
    }

    // =========================================================================
    // recordFailedAttempt
    // =========================================================================

    @Test
    @DisplayName("recordFailedAttempt - does NOT block on the 4th failure (below threshold)")
    void recordFailedAttempt_doesNotBlock_onFourthFailure() {
        // After upsert, simulate counter at 4 within the window
        LoginAttempt afterUpsert = buildAttempt(4, FIXED_NOW, null);
        when(loginAttemptRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(afterUpsert));

        service.recordFailedAttempt("user@test.com", "1.2.3.4", FIXED_NOW);

        verify(loginAttemptRepository).upsertAttempt("user@test.com", "1.2.3.4", FIXED_NOW, FIXED_NOW.minusMinutes(LoginAttemptService.WINDOW_MINUTES));
        // Block must NOT have been set
        verify(loginAttemptRepository, never()).setBlocked(any(), any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("recordFailedAttempt - DOES block on the 5th failure within the window")
    void recordFailedAttempt_blocksAccount_onFifthFailureWithinWindow() {
        // After upsert, simulate counter at exactly MAX_ATTEMPTS within the window
        LoginAttempt afterUpsert = buildAttempt(
                LoginAttemptService.MAX_ATTEMPTS, FIXED_NOW, null);
        when(loginAttemptRepository.findByEmail("victim@test.com"))
                .thenReturn(Optional.of(afterUpsert));

        service.recordFailedAttempt("victim@test.com", "5.5.5.5", FIXED_NOW);

        verify(loginAttemptRepository).setBlocked(
                "victim@test.com",
                FIXED_NOW.plusMinutes(LoginAttemptService.BLOCK_MINUTES));
        verify(auditoriaService).registrarAccion(eq(0L), eq("BLOQUEO_LOGIN"), any(String.class));
    }

    @Test
    @DisplayName("recordFailedAttempt - does NOT block if window has expired (stale counter)")
    void recordFailedAttempt_doesNotBlock_whenWindowExpired() {
        // After upsert, simulate counter at MAX_ATTEMPTS but last_attempt is OUTSIDE the window
        LoginAttempt staleAttempt = buildAttempt(
                LoginAttemptService.MAX_ATTEMPTS,
                FIXED_NOW.minusMinutes(LoginAttemptService.WINDOW_MINUTES + 1), // outside window
                null);
        when(loginAttemptRepository.findByEmail("stale@test.com"))
                .thenReturn(Optional.of(staleAttempt));

        service.recordFailedAttempt("stale@test.com", "9.9.9.9", FIXED_NOW);

        verify(loginAttemptRepository, never()).setBlocked(any(), any());
        verifyNoInteractions(auditoriaService);
    }

    // =========================================================================
    // resetOnSuccess
    // =========================================================================

    @Test
    @DisplayName("resetOnSuccess - delegates to repository resetAttempts")
    void resetOnSuccess_delegatesToRepository() {
        service.resetOnSuccess("good@test.com");
        verify(loginAttemptRepository).resetAttempts("good@test.com");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private LoginAttempt buildAttempt(int attempts, LocalDateTime lastAttempt, LocalDateTime blockedUntil) {
        return new LoginAttempt(1L, "test@test.com", "127.0.0.1", attempts, lastAttempt, blockedUntil);
    }
}
