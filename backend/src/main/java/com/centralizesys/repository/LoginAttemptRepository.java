package com.centralizesys.repository;

import com.centralizesys.model.auth.LoginAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for brute-force login attempt tracking.
 *
 * <p>There is at most one row per email address. Consecutive failed attempts
 * increment the counter atomically via an upsert. A successful login resets
 * the counter to zero. The scheduler calls {@link #deleteExpiredBlocks} to
 * prune records whose block window has long since passed.
 */
@Repository
public class LoginAttemptRepository {

    private static final String PARAM_EMAIL         = "email";
    private static final String PARAM_IP            = "ipAddress";
    private static final String PARAM_NOW           = "now";
    private static final String PARAM_BLOCKED_UNTIL = "blockedUntil";
    private static final String PARAM_THRESHOLD     = "threshold";

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private final RowMapper<LoginAttempt> rowMapper = (rs, rowNum) -> new LoginAttempt(
            rs.getLong("id"),
            rs.getString(PARAM_EMAIL),
            rs.getString("ip_address"),
            rs.getInt("attempts"),
            rs.getObject("last_attempt", LocalDateTime.class),
            rs.getObject("blocked_until", LocalDateTime.class));

    public LoginAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Returns the current attempt record for the given email, if any.
     *
     * @param email the email address to look up.
     * @return an {@link Optional} containing the record, or empty if none exists.
     */
    public Optional<LoginAttempt> findByEmail(String email) {
        String sql = "SELECT * FROM login_attempts WHERE email = :email";
        List<LoginAttempt> results = namedJdbcTemplate.query(
                sql, new MapSqlParameterSource(PARAM_EMAIL, email), rowMapper);
        return results.stream().findFirst();
    }

    /**
     * Atomically inserts or increments the failed-attempt counter for the given email.
     *
     * <p>On conflict (same email), the counter is incremented by 1 and
     * {@code last_attempt} / {@code ip_address} are updated.
     *
     * @param email     the email address used in the attempt.
     * @param ipAddress the remote IP address (may be null).
     * @param now       the current timestamp.
     */
    public void upsertAttempt(String email, String ipAddress, LocalDateTime now, LocalDateTime windowStart) {
        String sql = """
                INSERT INTO login_attempts (email, ip_address, attempts, last_attempt)
                VALUES (:email, :ipAddress, 1, :now)
                ON CONFLICT (email)
                DO UPDATE SET
                    attempts     = CASE WHEN login_attempts.last_attempt < :windowStart THEN 1 ELSE login_attempts.attempts + 1 END,
                    ip_address   = EXCLUDED.ip_address,
                    last_attempt = EXCLUDED.last_attempt
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_EMAIL, email)
                .addValue(PARAM_IP, ipAddress)
                .addValue(PARAM_NOW, now)
                .addValue("windowStart", windowStart);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Sets the {@code blocked_until} timestamp on the attempt record for the given email.
     * Called after the threshold of consecutive failures is exceeded.
     *
     * @param email        the email address to block.
     * @param blockedUntil the timestamp until which the account is blocked.
     */
    public void setBlocked(String email, LocalDateTime blockedUntil) {
        String sql = "UPDATE login_attempts SET blocked_until = :blockedUntil WHERE email = :email";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_BLOCKED_UNTIL, blockedUntil)
                .addValue(PARAM_EMAIL, email);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Resets the attempt counter and clears the block for the given email.
     * Called on every successful login.
     *
     * @param email the email address whose record should be reset.
     */
    public void resetAttempts(String email) {
        String sql = """
                UPDATE login_attempts
                SET attempts = 0, blocked_until = NULL
                WHERE email = :email
                """;
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_EMAIL, email));
    }

    /**
     * Deletes records whose block period ended before the given threshold.
     * Intended for use by the scheduled cleanup task.
     *
     * @param threshold records with {@code blocked_until} before this time are removed.
     */
    public void deleteExpiredBlocks(LocalDateTime threshold) {
        String sql = "DELETE FROM login_attempts WHERE blocked_until IS NOT NULL AND blocked_until < :threshold";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_THRESHOLD, threshold));
    }
}
