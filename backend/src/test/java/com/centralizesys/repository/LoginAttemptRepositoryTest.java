package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.LoginAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptRepositoryTest extends BaseIntegrationTest {

    // Fixed reference date to comply with Sonar S5977 "Do not use the system clock in tests"
    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, Month.JANUARY, 15, 10, 0, 0);

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    @DisplayName("findByEmail - returns empty for unknown email")
    void findByEmail_returnsEmpty_forUnknownEmail() {
        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail("nobody@test.com");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("upsertAttempt - inserts a new record on first call")
    void upsertAttempt_insertsNewRecord_onFirstCall() {
        loginAttemptRepository.upsertAttempt("first@test.com", "127.0.0.1", FIXED_NOW, FIXED_NOW.minusMinutes(15));

        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail("first@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("first@test.com");
        assertThat(result.get().getAttempts()).isEqualTo(1);
        assertThat(result.get().getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(result.get().getBlockedUntil()).isNull();
    }

    @Test
    @DisplayName("upsertAttempt - increments counter on subsequent calls within window")
    void upsertAttempt_incrementsCounter_onSubsequentCalls() {
        String email = "repeat@test.com";
        loginAttemptRepository.upsertAttempt(email, "10.0.0.1", FIXED_NOW, FIXED_NOW.minusMinutes(15));
        loginAttemptRepository.upsertAttempt(email, "10.0.0.1", FIXED_NOW.plusMinutes(1), FIXED_NOW.plusMinutes(1).minusMinutes(15));
        loginAttemptRepository.upsertAttempt(email, "10.0.0.1", FIXED_NOW.plusMinutes(2), FIXED_NOW.plusMinutes(2).minusMinutes(15));

        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getAttempts()).isEqualTo(3);
        assertThat(result.get().getLastAttempt()).isEqualTo(FIXED_NOW.plusMinutes(2));
    }

    @Test
    @DisplayName("upsertAttempt - resets counter if previous attempt was outside window")
    void upsertAttempt_resetsCounter_whenOutsideWindow() {
        String email = "resetWindow@test.com";
        loginAttemptRepository.upsertAttempt(email, "10.0.0.1", FIXED_NOW.minusMinutes(20), FIXED_NOW.minusMinutes(35));

        // Next attempt is outside the 15-minute window of the first attempt
        loginAttemptRepository.upsertAttempt(email, "10.0.0.1", FIXED_NOW, FIXED_NOW.minusMinutes(15));

        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getAttempts()).isEqualTo(1);
        assertThat(result.get().getLastAttempt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("setBlocked - persists the blocked_until timestamp")
    void setBlocked_persistsBlockedUntil() {
        String email = "blocked@test.com";
        loginAttemptRepository.upsertAttempt(email, null, FIXED_NOW, FIXED_NOW.minusMinutes(15));

        LocalDateTime blockExpiry = FIXED_NOW.plusMinutes(30);
        loginAttemptRepository.setBlocked(email, blockExpiry);

        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getBlockedUntil()).isEqualTo(blockExpiry);
    }

    @Test
    @DisplayName("resetAttempts - zeroes counter and clears block")
    void resetAttempts_zeroesCounterAndClearsBlock() {
        String email = "reset@test.com";
        loginAttemptRepository.upsertAttempt(email, null, FIXED_NOW, FIXED_NOW.minusMinutes(15));
        loginAttemptRepository.upsertAttempt(email, null, FIXED_NOW.plusMinutes(1), FIXED_NOW.plusMinutes(1).minusMinutes(15));
        loginAttemptRepository.setBlocked(email, FIXED_NOW.plusMinutes(30));

        loginAttemptRepository.resetAttempts(email);

        Optional<LoginAttempt> result = loginAttemptRepository.findByEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getAttempts()).isZero();
        assertThat(result.get().getBlockedUntil()).isNull();
    }

    @Test
    @DisplayName("deleteExpiredBlocks - removes records with a block that ended before threshold")
    void deleteExpiredBlocks_removesExpiredRecords() {
        String expiredEmail  = "expired@test.com";
        String activeEmail   = "active@test.com";
        String noBlockEmail  = "noblock@test.com";

        // Record 1: block expired 1 hour ago
        loginAttemptRepository.upsertAttempt(expiredEmail, null, FIXED_NOW, FIXED_NOW.minusMinutes(15));
        loginAttemptRepository.setBlocked(expiredEmail, FIXED_NOW.minusHours(1));

        // Record 2: block still active (expires 1 hour from now)
        loginAttemptRepository.upsertAttempt(activeEmail, null, FIXED_NOW, FIXED_NOW.minusMinutes(15));
        loginAttemptRepository.setBlocked(activeEmail, FIXED_NOW.plusHours(1));

        // Record 3: no block at all (should NOT be deleted by this method)
        loginAttemptRepository.upsertAttempt(noBlockEmail, null, FIXED_NOW, FIXED_NOW.minusMinutes(15));

        // Act: clean blocks that ended before FIXED_NOW
        loginAttemptRepository.deleteExpiredBlocks(FIXED_NOW);

        // Assert
        assertThat(loginAttemptRepository.findByEmail(expiredEmail)).isEmpty();
        assertThat(loginAttemptRepository.findByEmail(activeEmail)).isPresent();
        assertThat(loginAttemptRepository.findByEmail(noBlockEmail)).isPresent();
    }
}
