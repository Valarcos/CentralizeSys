package com.centralizesys.repository;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.enums.DiscountType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VentaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private static final String VENTA_ID = "venta_id";

    public VentaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // --- MAPPERS ---
    private final RowMapper<Venta> ventaMapper = (rs, rowNum) -> new Venta(
            rs.getLong("id"),
            rs.getString("fecha"),
            rs.getString("cliente_nombre"),
            rs.getDouble("total_venta"),
            rs.getObject("usuario_id", Long.class)
    );

    private final RowMapper<DetalleVenta> detalleMapper = (rs, rowNum) -> {
        String discountStr = rs.getString("descuento_tipo");
        DiscountType type;
        try {
            type = (discountStr != null) ? DiscountType.valueOf(discountStr) : DiscountType.NONE;
        } catch (IllegalArgumentException e) {
            type = DiscountType.NONE;
        }

        return new DetalleVenta(
                rs.getLong("id"),
                rs.getLong(VENTA_ID),
                rs.getLong("producto_id"),
                rs.getString("codigo_snapshot"),
                rs.getString("descripcion_snapshot"),
                rs.getLong("cantidad"),
                rs.getDouble("precio_lista"),
                type, // Use the safe variable
                rs.getDouble("descuento_valor"),
                rs.getDouble("precio_unitario"),
                rs.getDouble("subtotal")
        );
    };

    private final RowMapper<PagoVenta> pagoMapper = (rs, rowNum) -> new PagoVenta(
            rs.getLong("id"),
            rs.getLong(VENTA_ID),
            rs.getLong("metodo_pago_id"),
            rs.getDouble("monto")
    );

    // --- SAVE OPERATIONS ---

    /**
     * Saves the Header (Venta) and returns the generated ID.
     */
    public Long saveVenta(Venta venta) {
        // [NOTE] Using BeanPropertySqlParameterSource matches params to DTO fields automatically.
        String sql = """
            INSERT INTO ventas (fecha, cliente_nombre, total_venta, usuario_id)
            VALUES (:fecha, :clienteNombre, :totalVenta, :usuarioId)
        """;

        SqlParameterSource params = new BeanPropertySqlParameterSource(venta);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        namedJdbcTemplate.update(sql, params, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new InfrastructureException("La base de datos no devolvió el ID de la venta guardada.");
        }
        return key.longValue();
    }

    /**
     * Bulk inserts items.
     */
    public void saveDetalles(List<DetalleVenta> detalles) {
        String sql = """
            INSERT INTO detalles_venta
            (venta_id, producto_id, codigo_snapshot, descripcion_snapshot, cantidad, 
             precio_lista, descuento_tipo, descuento_valor, precio_unitario, subtotal) 
            VALUES (:ventaId,
                    :productoId,
                    :codigoSnapshot,
                    :descripcionSnapshot,
                    :cantidad, 
                    :precioLista,
                    :descuentoTipo,
                    :descuentoValor,
                    :precioUnitario,
                    :subtotal)
        """;

        // Manual mapping ensures we handle Enum.name() correctly
        List<MapSqlParameterSource> batchParams = detalles.stream()
                .map(d -> new MapSqlParameterSource()
                        .addValue(VENTA_ID, d.getVentaId())
                        .addValue("productoId", d.getProductoId())
                        .addValue("codigoSnapshot", d.getCodigoSnapshot())
                        .addValue("descripcionSnapshot", d.getDescripcionSnapshot())
                        .addValue("cantidad", d.getCantidad())
                        .addValue("precioLista", d.getPrecioLista())
                        .addValue("descuentoTipo", d.getDescuentoTipo().name()) // Enum to String
                        .addValue("descuentoValor", d.getDescuentoValor())
                        .addValue("precioUnitario", d.getPrecioUnitario())
                        .addValue("subtotal", d.getSubtotal()))
                .toList();

        namedJdbcTemplate.batchUpdate(sql, batchParams.toArray(new MapSqlParameterSource[0]));
    }

    /**
     * Bulk inserts payments.
     */
    public void savePagos(List<PagoVenta> pagos) {
        if (pagos.isEmpty()) return;
        String sql = "INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto) VALUES (:ventaId, :metodoPagoId, :monto)";
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(pagos);
        namedJdbcTemplate.batchUpdate(sql, batch);
    }

    // --- READ OPERATIONS ---

    public List<Venta> findAll() {
        return jdbcTemplate.query("SELECT * FROM ventas ORDER BY fecha DESC, id DESC", ventaMapper);
    }

    public Optional<Venta> findById(Long id) {
        String sql = "SELECT * FROM ventas WHERE id = :id";
        List<Venta> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ventaMapper);
        return list.stream().findFirst();
    }

    public List<DetalleVenta> findDetallesByVentaId(Long ventaId) {
        String sql = "SELECT * FROM detalles_venta WHERE venta_id = :ventaId";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource("ventaId", ventaId), detalleMapper);
    }

    public List<PagoVenta> findPagosByVentaId(Long ventaId) {
        String sql = "SELECT * FROM pagos_venta WHERE venta_id = :ventaId";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource("ventaId", ventaId), pagoMapper);
    }
}