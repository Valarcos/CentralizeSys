package com.centralizesys.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single active JWT session for a user.
 * Enforces the single-session-per-user constraint: when a user logs in,
 * any existing token for that user is deleted before this new one is inserted.
 *
 * <p>The 'jti' field corresponds to the JWT ID claim (RFC 7519 §4.1.7).
 * It is a UUID generated at token creation time and embedded in the JWT payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveToken {

    private Long id;

    /** The ID of the authenticated user who owns this session. */
    private Long usuarioId;

    /** JWT ID claim (UUID). Unique across the table via a unique index. */
    private String jti;

    /** Mirrors the JWT expiration claim. Used for scheduled cleanup. */
    private LocalDateTime expiresAt;

    /** Timestamp when the token was first issued. */
    private LocalDateTime createdAt;
}
