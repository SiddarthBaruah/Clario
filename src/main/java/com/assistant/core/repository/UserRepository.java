package com.assistant.core.repository;

import com.assistant.core.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<User> ROW_MAPPER = new UserRowMapper();

    public UserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email, username, phone_number, password_hash, created_at FROM users WHERE email = :email";
        List<User> users = jdbcTemplate.query(sql, new MapSqlParameterSource("email", email), ROW_MAPPER);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        String sql = "SELECT id, name, email, username, phone_number, password_hash, created_at FROM users WHERE phone_number = :phone_number";
        List<User> users = jdbcTemplate.query(sql, new MapSqlParameterSource("phone_number", phoneNumber), ROW_MAPPER);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, name, email, username, phone_number, password_hash, created_at FROM users WHERE id = :id";
        List<User> users = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public User save(User user) {
        if (user.getId() == null) {
            return insert(user);
        }
        update(user);
        return user;
    }

    private User insert(User user) {
        String sql = """
                INSERT INTO users (name, email, username, phone_number, password_hash)
                VALUES (:name, :email, :username, :phone_number, :password_hash)
                """;
        String username = user.getUsername() != null ? user.getUsername() : user.getEmail();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", user.getName())
                .addValue("email", user.getEmail())
                .addValue("username", username)
                .addValue("phone_number", user.getPhoneNumber())
                .addValue("password_hash", user.getPasswordHash());
        jdbcTemplate.update(sql, params);
        return findByEmail(user.getEmail()).orElseThrow();
    }

    private void update(User user) {
        String sql = """
                UPDATE users SET name = :name, email = :email, username = :username, phone_number = :phone_number, password_hash = :password_hash
                WHERE id = :id
                """;
        String username = user.getUsername() != null ? user.getUsername() : user.getEmail();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", user.getId())
                .addValue("name", user.getName())
                .addValue("email", user.getEmail())
                .addValue("username", username)
                .addValue("phone_number", user.getPhoneNumber())
                .addValue("password_hash", user.getPasswordHash());
        jdbcTemplate.update(sql, params);
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setEmail(rs.getString("email"));
            user.setUsername(rs.getString("username"));
            user.setPhoneNumber(rs.getString("phone_number"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
            return user;
        }
    }
}
