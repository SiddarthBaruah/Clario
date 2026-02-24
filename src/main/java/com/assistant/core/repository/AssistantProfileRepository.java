package com.assistant.core.repository;

import com.assistant.core.model.AssistantProfile;
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
public class AssistantProfileRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<AssistantProfile> ROW_MAPPER = new AssistantProfileRowMapper();

    public AssistantProfileRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AssistantProfile> findById(Long id) {
        String sql = "SELECT id, user_id, assistant_name, personality_prompt, created_at FROM assistant_profile WHERE id = :id";
        List<AssistantProfile> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AssistantProfile> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, assistant_name, personality_prompt, created_at FROM assistant_profile WHERE user_id = :user_id";
        List<AssistantProfile> list = jdbcTemplate.query(sql, new MapSqlParameterSource("user_id", userId), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<AssistantProfile> findAll() {
        String sql = "SELECT id, user_id, assistant_name, personality_prompt, created_at FROM assistant_profile ORDER BY id";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public AssistantProfile save(AssistantProfile profile) {
        if (profile.getId() == null) {
            return insert(profile);
        }
        update(profile);
        return profile;
    }

    public int deleteById(Long id) {
        String sql = "DELETE FROM assistant_profile WHERE id = :id";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    private AssistantProfile insert(AssistantProfile profile) {
        String sql = "INSERT INTO assistant_profile (user_id, assistant_name, personality_prompt) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, profile.getUserId());
            ps.setString(2, profile.getAssistantName());
            ps.setString(3, profile.getPersonalityPrompt());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    private void update(AssistantProfile profile) {
        String sql = """
                UPDATE assistant_profile SET user_id = :user_id, assistant_name = :assistant_name, personality_prompt = :personality_prompt
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", profile.getId())
                .addValue("user_id", profile.getUserId())
                .addValue("assistant_name", profile.getAssistantName())
                .addValue("personality_prompt", profile.getPersonalityPrompt());
        jdbcTemplate.update(sql, params);
    }

    private static class AssistantProfileRowMapper implements RowMapper<AssistantProfile> {
        @Override
        public AssistantProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            AssistantProfile p = new AssistantProfile();
            p.setId(rs.getLong("id"));
            p.setUserId(rs.getLong("user_id"));
            p.setAssistantName(rs.getString("assistant_name"));
            p.setPersonalityPrompt(rs.getString("personality_prompt"));
            p.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            return p;
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
