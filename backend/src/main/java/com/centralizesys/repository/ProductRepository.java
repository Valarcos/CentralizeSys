package com.centralizesys.repository;

import com.centralizesys.model.Product;
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
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Product> rowMapper = (rs, rowNum) ->
        new Product(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("stock"),
            rs.getDouble("price")
        );

    public List<Product> findAll() {
        String sql = "SELECT * FROM products";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        List<Product> products = jdbcTemplate.query(sql, rowMapper, id);
        return products.stream().findFirst();
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            // Insert
            String sql = "INSERT INTO products (name, stock, price) VALUES (?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            // the KeyHolder is used to retrieve the generated key in DB after the insert so it's usable
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, product.getName());
                ps.setInt(2, product.getStock());
                ps.setDouble(3, product.getPrice());
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key != null) {
                product.setId(key.longValue());
            }
        } else {
            // Update
            String sql = "UPDATE products SET name = ?, stock = ?, price = ? WHERE id = ?";
            jdbcTemplate.update(sql, product.getName(), product.getStock(), product.getPrice(), product.getId());
        }
        return product;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM products WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count > 0;
    }
}