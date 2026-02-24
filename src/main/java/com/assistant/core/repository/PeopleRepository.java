package com.assistant.core.repository;

import com.assistant.core.model.People;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PeopleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<People> ROW_MAPPER = new PeopleRowMapper();

    public PeopleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<People> findById(Long id) {
        String sql = "SELECT id, user_id, name, notes, important_dates, created_at FROM people WHERE id = :id";
        List<People> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<People> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, name, notes, important_dates, created_at FROM people WHERE user_id = :user_id ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("user_id", userId), ROW_MAPPER);
    }

    public List<People> findByUserId(Long userId, int limit, int offset) {
        String sql = "SELECT id, user_id, name, notes, important_dates, created_at FROM people WHERE user_id = :user_id ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public long countByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM people WHERE user_id = :user_id";
        Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("user_id", userId), Long.class);
        return count != null ? count : 0;
    }

    public People save(People people) {
        if (people.getId() == null) {
            return insert(people);
        }
        update(people);
        return people;
    }

    public int deleteById(Long id) {
        String sql = "DELETE FROM people WHERE id = :id";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    private People insert(People people) {
        String sql = "INSERT INTO people (user_id, name, notes, important_dates) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, people.getUserId());
            ps.setString(2, people.getName());
            ps.setString(3, people.getNotes());
            ps.setString(4, people.getImportantDates());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    private void update(People people) {
        String sql = """
                UPDATE people SET user_id = :user_id, name = :name, notes = :notes, important_dates = :important_dates
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", people.getId())
                .addValue("user_id", people.getUserId())
                .addValue("name", people.getName())
                .addValue("notes", people.getNotes())
                .addValue("important_dates", people.getImportantDates());
        jdbcTemplate.update(sql, params);
    }

    private static class PeopleRowMapper implements RowMapper<People> {
        @Override
        public People mapRow(ResultSet rs, int rowNum) throws SQLException {
            People p = new People();
            p.setId(rs.getLong("id"));
            p.setUserId(rs.getLong("user_id"));
            p.setName(rs.getString("name"));
            p.setNotes(rs.getString("notes"));
            p.setImportantDates(rs.getString("important_dates"));
            p.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            return p;
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
