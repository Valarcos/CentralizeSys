package com.centralizesys.repository;

import com.centralizesys.model.purchase.Compra;
import com.centralizesys.model.purchase.DetalleCompra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;

@Repository
public class CompraRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompraRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long saveCompra(Compra compra) {
        String sql = """
            INSERT INTO compras (fecha, proveedor, nro_comprobante, total_compra, usuario_id)
            VALUES (?, ?, ?, ?, ?)
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});

            ps.setString(1, compra.getFecha()); // ISO-8601
            ps.setString(2, compra.getProveedor());
            ps.setString(3, compra.getNroComprobante());
            ps.setDouble(4, compra.getTotalCompra());

            // Handle null usuarioId gracefully
            if (compra.getUsuarioId() != null) {
                ps.setLong(5, compra.getUsuarioId());
            } else {
                ps.setObject(5, null);
            }
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void saveDetalle(DetalleCompra detalle) {
        String sql = """
            INSERT INTO detalles_compra (compra_id, producto_id, cantidad, costo_unitario, subtotal)
            VALUES (?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                detalle.getCompraId(),
                detalle.getProductoId(),
                detalle.getCantidad(),
                detalle.getCostoUnitario(),
                detalle.getSubtotal());
    }

    public void saveDetalles(List<DetalleCompra> detalles) {
        String sql = """
            INSERT INTO detalles_compra (compra_id, producto_id, cantidad, costo_unitario, subtotal)
            VALUES (?, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, detalles, detalles.size(), (ps, detalle) -> {
            ps.setLong(1, detalle.getCompraId());
            ps.setLong(2, detalle.getProductoId());
            ps.setLong(3, detalle.getCantidad());
            ps.setDouble(4, detalle.getCostoUnitario());
            ps.setDouble(5, detalle.getSubtotal());
        });
    }
}