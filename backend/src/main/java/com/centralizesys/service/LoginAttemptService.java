package com.centralizesys.service;

import com.centralizesys.model.auth.LoginAttempt;
import com.centralizesys.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service responsible for brute-force login protection.
 *
 * <p><b>Business Rules:</b>
 * <ul>
 *   <li>A maximum of {@value #MAX_ATTEMPTS} failed attempts are allowed within a
 *       {@value #WINDOW_MINUTES}-minute sliding window.</li>
 *   <li>Exceeding the threshold locks the account for {@value #BLOCK_MINUTES} minutes.</li>
 *   <li>A successful login always resets the counter via {@link #resetOnSuccess}.</li>
 * </ul>
 *
 * <p>The package-private helper methods ({@code isBlocked}, {@code isWithinWindow}) are
 * intentionally left non-private for gray-box unit testing without reflection.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Maximum consecutive failures before the account is blocked. */
    static final int MAX_ATTEMPTS = 5;

    /** Time window (minutes) within which failed attempts are counted. */
    static final int WINDOW_MINUTES = 15;

    /** How long (minutes) a blocked account must wait before it can try again. */
    static final int BLOCK_MINUTES = 30;

    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditoriaService auditoriaService;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository,
                               AuditoriaService auditoriaService) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.auditoriaService = auditoriaService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Checks if the given email is currently blocked and throws {@link LockedException} if so.
     * Must be called BEFORE attempting authentication to give a clear, immediate rejection.
     *
     * @param email the email address being used in the login attempt.
     * @param now   the current timestamp (injected by the caller for testability).
     * @throws LockedException if the account is currently within its block period.
     */
    public void checkAndThrowIfBlocked(String email, LocalDateTime now) {
        Optional<LoginAttempt> attempt = loginAttemptRepository.findByEmail(email);
        if (attempt.isPresent() && isBlocked(attempt.get(), now)) {
            throw new LockedException("La cuenta ha sido bloqueada temporalmente por múltiples intentos fallidos.");
        }
    }

    /**
     * Records a failed login attempt for the given email. If this failure causes the attempt
     * counter to reach {@value #MAX_ATTEMPTS} within the sliding window, the account is blocked.
     *
     * <p>A block event is audited using System User ID 0, since the real user is not authenticated.
     *
     * @param email     the email address that failed to authenticate.
     * @param ipAddress the remote IP address (may be null).
     * @param now       the current timestamp.
     */
    public void recordFailedAttempt(String email, String ipAddress, LocalDateTime now) {
        LocalDateTime windowStart = now.minusMinutes(WINDOW_MINUTES);
        loginAttemptRepository.upsertAttempt(email, ipAddress, now, windowStart);

        Optional<LoginAttempt> updated = loginAttemptRepository.findByEmail(email);
        if (updated.isPresent()) {
            LoginAttempt attempt = updated.get();
            if (isWithinWindow(attempt, now) && attempt.getAttempts() >= MAX_ATTEMPTS) {
                LocalDateTime blockedUntil = now.plusMinutes(BLOCK_MINUTES);
                loginAttemptRepository.setBlocked(email, blockedUntil);
                log.warn("Login attempt threshold exceeded for email '{}'. Account blocked until {}.", email, blockedUntil);

                // Audit the block using System User (ID 0) — real user is not authenticated yet.
                String details = String.format(
                        "Cuenta bloqueada por %d intentos fallidos desde IP: %s. Bloqueada hasta: %s",
                        attempt.getAttempts(), ipAddress, blockedUntil);
                auditoriaService.registrarAccion(0L, "BLOQUEO_LOGIN", details);
            }
        }
    }

    /**
     * Resets the failed-attempt counter for the given email after a successful login.
     * This is a no-op if no prior failure record exists for the email.
     *
     * @param email the email address that successfully authenticated.
     */
    public void resetOnSuccess(String email) {
        loginAttemptRepository.resetAttempts(email);
    }

    // -------------------------------------------------------------------------
    // Package-Private Helpers (gray-box testable)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given attempt record indicates the account is
     * currently blocked (i.e., {@code blocked_until} is in the future).
     */
    boolean isBlocked(LoginAttempt attempt, LocalDateTime now) {
        return attempt.getBlockedUntil() != null && attempt.getBlockedUntil().isAfter(now);
    }

    /**
     * Returns {@code true} if the last failed attempt occurred within the
     * {@value #WINDOW_MINUTES}-minute sliding window relative to {@code now}.
     * Attempts older than the window are treated as a fresh slate.
     */
    boolean isWithinWindow(LoginAttempt attempt, LocalDateTime now) {
        return attempt.getLastAttempt() != null
                && attempt.getLastAttempt().isAfter(now.minusMinutes(WINDOW_MINUTES));
    }
}
