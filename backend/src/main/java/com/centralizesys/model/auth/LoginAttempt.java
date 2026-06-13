package com.centralizesys.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a brute-force tracking record for a given email address.
 * There is at most one record per email. The 'attempts' counter is reset
 * on successful login and incremented on each failure.
 *
 * <p>No FK to 'usuarios' by design: we track attempts for non-existent emails
 * to prevent bypass via slight email misspellings (enumeration hardening).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttempt {

    private Long id;

    /** The email address that was used in the login attempt. */
    private String email;

    /** IP address of the requester (nullable if not available). */
    private String ipAddress;

    /** Total number of consecutive failed attempts within the current window. */
    private Integer attempts;

    /** Timestamp of the last failed attempt. Used to determine the sliding window. */
    private LocalDateTime lastAttempt;

    /**
     * If non-null and in the future, the account is currently blocked.
     * A null value means the account is not blocked.
     */
    private LocalDateTime blockedUntil;
}
