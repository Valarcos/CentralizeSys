package com.centralizesys.repository;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.enums.DiscountType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class VentaRepository {

    private final JdbcTemplate jdbcTemplate;

    public VentaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            type = DiscountType.NONE; // Fallback safety
        }

        return new DetalleVenta(
                rs.getLong("id"),
                rs.getLong("venta_id"),
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
            rs.getLong("venta_id"),
            rs.getLong("metodo_pago_id"),
            rs.getDouble("monto")
    );

    // --- SAVE OPERATIONS ---

    /**
     * Saves the Header (Venta) and returns the generated ID.
     */
    public Long saveVenta(Venta venta) {
        String sql = "INSERT INTO ventas (fecha, cliente_nombre, total_venta, usuario_id) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, venta.getFecha());
            ps.setString(2, venta.getClienteNombre());
            ps.setDouble(3, venta.getTotalVenta());

            if (venta.getUsuarioId() != null) {
                ps.setLong(4, venta.getUsuarioId());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new InfrastructureException("La base de datos no devolvió el ID de la venta guardada.");
        }
        return key.longValue();
    }

    /**
     * Bulk inserts items.
     * Fixed: Batch size is now passed as a direct Long.
     */
    public void saveDetalles(List<DetalleVenta> detalles) {
        String sql = """
            INSERT INTO detalles_venta
            (venta_id, producto_id, codigo_snapshot, descripcion_snapshot, cantidad, 
             precio_lista, descuento_tipo, descuento_valor, precio_unitario, subtotal) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, detalles, detalles.size(), (ps, detalle) -> {
            ps.setLong(1, detalle.getVentaId());
            ps.setLong(2, detalle.getProductoId());
            ps.setString(3, detalle.getCodigoSnapshot());
            ps.setString(4, detalle.getDescripcionSnapshot());
            ps.setLong(5, detalle.getCantidad());

            // New Sets
            ps.setDouble(6, detalle.getPrecioLista());
            ps.setString(7, detalle.getDescuentoTipo().name()); // Enum -> String
            ps.setDouble(8, detalle.getDescuentoValor());

            ps.setDouble(9, detalle.getPrecioUnitario());
            ps.setDouble(10, detalle.getSubtotal());
        });
    }

    /**
     * Bulk inserts payments.
     */
    public void savePagos(List<PagoVenta> pagos) {
        if (pagos.isEmpty()) return;

        String sql = "INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto) VALUES (?, ?, ?)";

        jdbcTemplate.batchUpdate(
                sql,
                pagos,
                pagos.size(),
                (PreparedStatement ps, PagoVenta pago) -> {
                    ps.setLong(1, pago.getVentaId());
                    ps.setLong(2, pago.getMetodoPagoId());
                    ps.setDouble(3, pago.getMonto());
                }
        );
    }

    // --- READ OPERATIONS ---

    public List<Venta> findAll() {
        return jdbcTemplate.query("SELECT * FROM ventas ORDER BY fecha DESC, id DESC", ventaMapper);
    }

    public Optional<Venta> findById(Long id) {
        String sql = "SELECT * FROM ventas WHERE id = ?";
        List<Venta> list = jdbcTemplate.query(sql, ventaMapper, id);
        return list.stream().findFirst();
    }

    public List<DetalleVenta> findDetallesByVentaId(Long ventaId) {
        String sql = "SELECT * FROM detalles_venta WHERE venta_id = ?";
        return jdbcTemplate.query(sql, detalleMapper, ventaId);
    }

    public List<PagoVenta> findPagosByVentaId(Long ventaId) {
        String sql = "SELECT * FROM pagos_venta WHERE venta_id = ?";
        return jdbcTemplate.query(sql, pagoMapper, ventaId);
    }
}