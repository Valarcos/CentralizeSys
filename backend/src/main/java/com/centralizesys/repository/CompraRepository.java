package com.centralizesys.repository;

import com.centralizesys.model.purchase.Compra;
import com.centralizesys.model.purchase.DetalleCompra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class CompraRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public CompraRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public long saveCompra(Compra compra) {
        // Named parameters auto-handle NULLs for usuarioId
        String sql = """
            INSERT INTO compras (fecha, proveedor, nro_comprobante, total_compra, usuario_id)
            VALUES (:fecha, :proveedor, :nroComprobante, :totalCompra, :usuarioId)
        """;

        SqlParameterSource params = new BeanPropertySqlParameterSource(compra);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }



    public void saveDetalles(List<DetalleCompra> detalles) {
        String sql = """
            INSERT INTO detalles_compra (compra_id, producto_id, cantidad, costo_unitario, subtotal)
            VALUES (:compraId, :productoId, :cantidad, :costoUnitario, :subtotal)
        """;

        // [IMPROVED] One-line batch generation
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(detalles);
        namedJdbcTemplate.batchUpdate(sql, batch);
    }

    public List<Compra> findAll() {
        String sql = "SELECT * FROM compras";
        return namedJdbcTemplate.query(sql, (rs, rowNum) -> {
            Compra c = new Compra();
            c.setId(rs.getLong("id"));
            c.setFecha(rs.getObject("fecha", java.time.LocalDateTime.class));
            c.setProveedor(rs.getString("proveedor"));
            c.setNroComprobante(rs.getString("nro_comprobante"));
            c.setTotalCompra(rs.getDouble("total_compra"));
            c.setUsuarioId(rs.getObject("usuario_id") != null ? rs.getLong("usuario_id") : null);
            return c;
        });
    }
}