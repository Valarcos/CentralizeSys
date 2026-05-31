package com.centralizesys.repository;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public static final String EMAIL = "email";
    public static final String NOMBRE = "nombre";

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<Usuario> rowMapper = (rs, rowNum) -> new Usuario(
            rs.getLong("id"),
            rs.getString(NOMBRE),
            rs.getString(EMAIL),
            rs.getString("password_hash"),
            UsuarioRole.valueOf(rs.getString("rol")),
            rs.getObject("fecha_creacion", LocalDateTime.class));

    public Optional<Usuario> findByEmail(String email) {
        String sql = "SELECT * FROM usuarios WHERE email = :email";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource(EMAIL, email),
                rowMapper);
        return users.stream().findFirst();
    }

    public Optional<Usuario> findById(Long id) {
        String sql = "SELECT * FROM usuarios WHERE id = :id";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                rowMapper);
        return users.stream().findFirst();
    }

    public void save(Usuario usuario) {
        String sql = """
                    INSERT INTO usuarios (nombre, email, password_hash, rol)
                    VALUES (:nombre, :email, :passwordHash, :rol)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(NOMBRE, usuario.getNombre())
                .addValue(EMAIL, usuario.getEmail())
                .addValue("passwordHash", usuario.getPasswordHash())
                .addValue("rol", usuario.getRol().name());

        namedJdbcTemplate.update(sql, params);
    }

    public List<Usuario> findAll() {
        String sql = "SELECT * FROM usuarios";
        return namedJdbcTemplate.query(sql, rowMapper);
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM usuarios WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public void update(Usuario usuario) {
        String sql = """
                    UPDATE usuarios
                    SET nombre = :nombre, email = :email, password_hash = :passwordHash, rol = :rol
                    WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", usuario.getId())
                .addValue(NOMBRE, usuario.getNombre())
                .addValue(EMAIL, usuario.getEmail())
                .addValue("passwordHash", usuario.getPasswordHash())
                .addValue("rol", usuario.getRol().name());

        namedJdbcTemplate.update(sql, params);
    }
}