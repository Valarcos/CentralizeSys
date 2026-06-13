package com.centralizesys.repository;

import com.centralizesys.model.auth.ActiveToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing active JWT sessions.
 *
 * <p>Each row represents one live session. The single-session-per-user rule
 * is enforced by calling {@link #deleteByUsuarioId} before inserting a new token
 * on login. The JTI claim (UUID) serves as the stable identifier for each token.
 */
@Repository
public class ActiveTokenRepository {

    private static final String PARAM_USUARIO_ID = "usuarioId";
    private static final String PARAM_JTI        = "jti";
    private static final String PARAM_EXPIRES_AT = "expiresAt";
    private static final String PARAM_NOW        = "now";

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private final RowMapper<ActiveToken> rowMapper = (rs, rowNum) -> new ActiveToken(
            rs.getLong("id"),
            rs.getLong("usuario_id"),
            rs.getString("jti"),
            rs.getObject("expires_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class));

    public ActiveTokenRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Persists a new active token for the given user.
     *
     * @param usuarioId the ID of the authenticated user.
     * @param jti       the JWT ID claim (UUID).
     * @param expiresAt the token's expiration timestamp.
     */
    public void save(Long usuarioId, String jti, LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO active_tokens (usuario_id, jti, expires_at)
                VALUES (:usuarioId, :jti, :expiresAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_USUARIO_ID, usuarioId)
                .addValue(PARAM_JTI, jti)
                .addValue(PARAM_EXPIRES_AT, expiresAt);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Looks up a token by its JTI claim.
     * Used as the DB fallback when the in-memory cache does not contain the token.
     *
     * @param jti the JWT ID claim to search for.
     * @return an {@link Optional} containing the token, or empty if not found/revoked.
     */
    public Optional<ActiveToken> findByJti(String jti) {
        String sql = "SELECT * FROM active_tokens WHERE jti = :jti";
        List<ActiveToken> results = namedJdbcTemplate.query(
                sql, new MapSqlParameterSource(PARAM_JTI, jti), rowMapper);
        return results.stream().findFirst();
    }

    /**
     * Returns all active tokens that have not yet expired.
     * Used for cache warm-up on application startup.
     *
     * @param now the current timestamp; tokens expiring before this are excluded.
     * @return a list of all non-expired active tokens.
     */
    public List<ActiveToken> findAllValid(LocalDateTime now) {
        String sql = "SELECT * FROM active_tokens WHERE expires_at > :now";
        return namedJdbcTemplate.query(
                sql, new MapSqlParameterSource(PARAM_NOW, now), rowMapper);
    }

    /**
     * Deletes all tokens for the given user.
     * Called at the start of a new login to invalidate any prior session.
     *
     * @param usuarioId the user whose sessions are being invalidated.
     */
    public void deleteByUsuarioId(Long usuarioId) {
        String sql = "DELETE FROM active_tokens WHERE usuario_id = :usuarioId";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_USUARIO_ID, usuarioId));
    }

    /**
     * Deletes a single token by its JTI. Called on logout.
     *
     * @param jti the JWT ID claim of the token to revoke.
     */
    public void deleteByJti(String jti) {
        String sql = "DELETE FROM active_tokens WHERE jti = :jti";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_JTI, jti));
    }

    /**
     * Deletes all tokens that have already expired. Called by the scheduled cleanup task.
     *
     * @param now tokens expiring before this timestamp are deleted.
     */
    public void deleteExpired(LocalDateTime now) {
        String sql = "DELETE FROM active_tokens WHERE expires_at < :now";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_NOW, now));
    }
}
