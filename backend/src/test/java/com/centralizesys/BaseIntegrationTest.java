package com.centralizesys;

import com.centralizesys.config.TestDatabaseConfig;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.product.Location;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
// 1. Load our In-Memory Config
@Import(TestDatabaseConfig.class)
// 2. Prevent Spring from using H2 (We want SQLite)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 3. Rollback changes after every test method
@Transactional
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Common Repositories needed by most tests
    // Useful for quick assertions (e.g. "SELECT count(*) FROM...")
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected StockRepository stockRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Helper to create a default Admin user.
     * Most flows (Venta, Compra) require a valid user ID.
     * @return The ID of the created user.
     */
    protected Long createTestUser() {
        // Check if exists to avoid unique constraint errors if tests share context
        return usuarioRepository.findByEmail("test@admin.com")
                .map(Usuario::getId)
                .orElseGet(() -> {
                    Usuario u = new Usuario();
                    u.setNombre("Test Admin");
                    u.setEmail("test@admin.com");
                    u.setPasswordHash(passwordEncoder.encode("123456"));
                    usuarioRepository.save(u);

                    // Retrieve ID
                    return usuarioRepository.findByEmail("test@admin.com")
                            .orElseThrow(() -> new RuntimeException("User creation failed"))
                            .getId();
                });
    }

    /**
     * Helper to create a generic product with stock.
     * @param code The ART code (e.g. "A-100")
     * @param price Retail price
     * @param stock Qty to add
     * @return The ID of the created product.
     */
    protected Long createTestProduct(String code, Double price, Long stock) {
        // 1. Create Location if not exists (Idempotent check)
        Long locId = stockRepository.findAllLocations().stream()
                .filter(l -> l.getNombre().equals("1"))
                .findFirst()
                .map(Location::getId)
                .orElseGet(() -> stockRepository.createLocation("1"));

        // 2. Check if Product exists by Code AND Price
        List<Product> candidates = productRepository.findAllByCodigo(code);

        Product existingProduct = candidates.stream()
                .filter(p -> p.getPrecioMinorista().equals(price)) // Match strictly by price
                .findFirst()
                .orElse(null);

        Long prodId;
        if (existingProduct != null) {
            prodId = existingProduct.getId();
            // Reset stock for the test
            stockRepository.updateQuantity(prodId, locId, stock);
        } else {
            Product p = new Product(code, "Test Desc", price * 0.5, price * 0.8, price);
            Product saved = productRepository.save(p);
            prodId = saved.getId();
        }

        // 3. Update/Add Stock
        if (stock > 0) {
            stockRepository.updateQuantity(prodId, locId, stock);
        }

        return prodId;
    }

    // [MODIFIED] Use a cleaner check for data to ensure isolation
    @BeforeEach
    protected void cleanTransactionalData() {
        // 1. Delete dependent tables first (Foreign Key Order)
        // We MUST delete 'auditoria' because it references Users
        jdbcTemplate.execute("DELETE FROM auditoria");

        // Sales Cycle
        jdbcTemplate.execute("DELETE FROM deudores");
        jdbcTemplate.execute("DELETE FROM pagos_venta");
        jdbcTemplate.execute("DELETE FROM detalles_venta");
        jdbcTemplate.execute("DELETE FROM ventas");

        // Purchase Cycle (Missing in your previous code)
        jdbcTemplate.execute("DELETE FROM detalles_compra");
        jdbcTemplate.execute("DELETE FROM compras");

        // Inventory
        jdbcTemplate.execute("DELETE FROM stock_por_ubicacion");

        // 2. Clear Products (Safe to delete as long as stock/sales are gone)
        jdbcTemplate.execute("DELETE FROM productos");

        // 3. DO NOT DELETE 'usuarios' or 'ubicaciones'
        // schema.sql inserts 'Administrador'. If we delete it, we might break
        // assumptions or future tests. Since we deleted 'auditoria' and 'ventas',
        // keeping the User records is harmless and prevents FK violations.
    }
}