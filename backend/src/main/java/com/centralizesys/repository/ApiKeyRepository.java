package com.centralizesys.repository;

import com.centralizesys.model.auth.ApiKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ApiKeyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final RowMapper<ApiKey> rowMapper = (rs, rowNum) -> ApiKey.builder()
            .id(rs.getLong("id"))
            .nombre(rs.getString("nombre"))
            .keyHash(rs.getString("key_hash"))
            .activo(rs.getBoolean("activo"))
            .creadoEn(rs.getTimestamp("creado_en") != null
                    ? rs.getTimestamp("creado_en").toLocalDateTime() : null)
            .ultimoAcceso(rs.getTimestamp("ultimo_acceso") != null
                    ? rs.getTimestamp("ultimo_acceso").toLocalDateTime() : null)
            .rateLimitRpm(rs.getInt("rate_limit_rpm"))
            .build();

    public ApiKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public Optional<ApiKey> findByHash(String keyHash) {
        String sql = "SELECT * FROM api_keys WHERE key_hash = :keyHash AND activo = true";
        List<ApiKey> results = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("keyHash", keyHash),
                rowMapper
        );
        return results.stream().findFirst();
    }

    public ApiKey save(ApiKey apiKey) {
        String sql = """
                INSERT INTO api_keys (nombre, key_hash, activo, creado_en, ultimo_acceso, rate_limit_rpm)
                VALUES (:nombre, :keyHash, :activo, :creadoEn, :ultimoAcceso, :rateLimitRpm)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nombre", apiKey.getNombre())
                .addValue("keyHash", apiKey.getKeyHash())
                .addValue("activo", apiKey.isActivo())
                .addValue("creadoEn", apiKey.getCreadoEn())
                .addValue("ultimoAcceso", apiKey.getUltimoAcceso())
                .addValue("rateLimitRpm", apiKey.getRateLimitRpm());

        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key != null) {
            apiKey.setId(key.longValue());
        }
        return apiKey;
    }

    /**
     * Throttled update of the last accessed timestamp.
     * Only updates the row if the timestamp is null or older than the threshold (e.g. 5 minutes).
     * This mitigates row-lock contention and database write churn under rapid successive calls.
     */
    public void updateUltimoAccesoThrottled(Long id, LocalDateTime now, LocalDateTime threshold) {
        String sql = """
                UPDATE api_keys
                SET ultimo_acceso = :now
                WHERE id = :id AND (ultimo_acceso IS NULL OR ultimo_acceso < :threshold)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("now", now)
                .addValue("threshold", threshold);
        namedParameterJdbcTemplate.update(sql, params);
    }

    public void revoke(Long id) {
        String sql = "UPDATE api_keys SET activo = false WHERE id = :id";
        namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public List<ApiKey> findAll() {
        String sql = "SELECT * FROM api_keys ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }
}
