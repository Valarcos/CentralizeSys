package com.centralizesys.repository;

import com.centralizesys.model.gastos.GastoCaja;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class GastoCajaRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public GastoCajaRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    private final RowMapper<GastoCaja> rowMapper = (rs, rowNum) -> {
        GastoCaja gasto = new GastoCaja();
        gasto.setId(rs.getLong("id"));
        gasto.setMonto(rs.getDouble("monto"));
        gasto.setMotivo(rs.getString("motivo"));
        gasto.setFechaGasto(rs.getObject("fecha_gasto", LocalDateTime.class));
        gasto.setFechaRegistro(rs.getObject("fecha_registro", LocalDateTime.class));
        gasto.setPersonaInvolucrada(rs.getString("persona_involucrada"));
        Number usuarioId = (Number) rs.getObject("registrado_por_usuario_id");
        gasto.setRegistradoPorUsuarioId(usuarioId != null ? usuarioId.longValue() : null);
        gasto.setCategoria(rs.getString("categoria"));
        gasto.setAnulado(rs.getBoolean("anulado"));
        gasto.setRazonAnulacion(rs.getString("razon_anulacion"));

        // Use left join to fetch the user's name if joined
        try {
            gasto.setRegistradoPorUsuarioNombre(rs.getString("usuario_nombre"));
        } catch (Exception e) {
            // Field not in result set, ignore
        }
        return gasto;
    };

    public Long save(GastoCaja gasto) {
        String sql = """
                INSERT INTO gastos_caja (monto, motivo, fecha_gasto, fecha_registro, 
                                         persona_involucrada, registrado_por_usuario_id, categoria, anulado)
                VALUES (:monto, :motivo, :fechaGasto, :fechaRegistro, 
                        :personaInvolucrada, :registradoPorUsuarioId, :categoria, false)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monto", gasto.getMonto())
                .addValue("motivo", gasto.getMotivo())
                .addValue("fechaGasto", gasto.getFechaGasto())
                .addValue("fechaRegistro", gasto.getFechaRegistro())
                .addValue("personaInvolucrada", gasto.getPersonaInvolucrada())
                .addValue("registradoPorUsuarioId", gasto.getRegistradoPorUsuarioId())
                .addValue("categoria", gasto.getCategoria());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void anular(Long id, String razonAnulacion) {
        String sql = """
                UPDATE gastos_caja
                SET anulado = true, razon_anulacion = :razonAnulacion
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("razonAnulacion", razonAnulacion);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * Retrieves a page of Gastos, optionally filtered by date granularity.
     * The date filter applies to `fecha_gasto`. All parameters are nullable —
     * when omitted, that granularity level is not applied.
     *
     * @param limit  Number of rows to return (page size).
     * @param offset Row offset (page * size).
     * @param year   Filter year (required when month or day are provided).
     * @param month  Filter month 1-12 (nullable).
     * @param day    Filter day 1-31 (nullable).
     */
    @SuppressWarnings("java:S2077")
    public List<GastoCaja> findAll(Long limit, Long offset, Integer year, Integer month, Integer day) {
        MapSqlParameterSource params = buildDateParams(year, month, day);
        params.addValue("limit", limit).addValue("offset", offset);
        String sql = """
                SELECT g.*, u.nombre as usuario_nombre
                FROM gastos_caja g
                LEFT JOIN usuarios u ON g.registrado_por_usuario_id = u.id
                WHERE 1=1
                """ + buildFechaFilter(year, month, day) + """
                 ORDER BY g.fecha_gasto DESC
                LIMIT :limit OFFSET :offset
                """;
        return namedJdbcTemplate.query(sql, params, rowMapper);
    }

    /**
     * Returns the total count of Gastos matching the same optional date filters.
     * Used to calculate `totalPages` for the pagination metadata.
     */
    @SuppressWarnings("java:S2077")
    public Long countAll(Integer year, Integer month, Integer day) {
        MapSqlParameterSource params = buildDateParams(year, month, day);
        String sql = "SELECT COUNT(*) FROM gastos_caja g WHERE 1=1"
                + buildFechaFilter(year, month, day);
        Long count = namedJdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Builds a date filter fragment for the `fecha_gasto` column.
     * Uses `:year`, `:month`, `:day` named params (present in params source when non-null).
     */
    private String buildFechaFilter(Integer year, Integer month, Integer day) {
        StringBuilder sb = new StringBuilder();
        if (year  != null) sb.append(" AND EXTRACT(YEAR  FROM g.fecha_gasto) = :year");
        if (month != null) sb.append(" AND EXTRACT(MONTH FROM g.fecha_gasto) = :month");
        if (day   != null) sb.append(" AND EXTRACT(DAY   FROM g.fecha_gasto) = :day");
        return sb.toString();
    }

    /**
     * Builds the named parameter source for the date filter.
     */
    private MapSqlParameterSource buildDateParams(Integer year, Integer month, Integer day) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (year  != null) params.addValue("year",  year);
        if (month != null) params.addValue("month", month);
        if (day   != null) params.addValue("day",   day);
        return params;
    }

    public Optional<GastoCaja> findById(Long id) {
        String sql = """
                SELECT g.*, u.nombre as usuario_nombre
                FROM gastos_caja g
                LEFT JOIN usuarios u ON g.registrado_por_usuario_id = u.id
                WHERE g.id = :id
                """;
        List<GastoCaja> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return list.stream().findFirst();
    }
}
