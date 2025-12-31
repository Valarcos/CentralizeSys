package com.centralizesys.repository;

import com.centralizesys.model.auth.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public static final String EMAIL = "email";

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<Usuario> rowMapper = (rs, rowNum) -> new Usuario(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString(EMAIL),
            rs.getString("password_hash"),
            rs.getString("fecha_creacion")
    );

    public Optional<Usuario> findByEmail(String email) {
        String sql = "SELECT * FROM usuarios WHERE email = :email";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource(EMAIL, email),
                rowMapper
        );
        return users.stream().findFirst();
    }

    public Optional<Usuario> findById(Long id) {
        String sql = "SELECT * FROM usuarios WHERE id = :id";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                rowMapper
        );
        return users.stream().findFirst();
    }

    public void save(Usuario usuario) {
        String sql = """
            INSERT INTO usuarios (nombre, email, password_hash)
            VALUES (:nombre, :email, :passwordHash)
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nombre", usuario.getNombre())
                .addValue(EMAIL, usuario.getEmail())
                .addValue("passwordHash", usuario.getPasswordHash());

        namedJdbcTemplate.update(sql, params);
    }
}