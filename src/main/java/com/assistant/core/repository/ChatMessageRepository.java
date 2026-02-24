package com.assistant.core.repository;

import com.assistant.core.model.ChatMessage;
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

@Repository
public class ChatMessageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<ChatMessage> ROW_MAPPER = new ChatMessageRowMapper();

    public ChatMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessage save(ChatMessage message) {
        String sql = "INSERT INTO chat_messages (user_id, role, content) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.getUserId());
            ps.setString(2, message.getRole());
            ps.setString(3, message.getContent());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        message.setId(id);
        return message;
    }

    /**
     * Returns the last {@code limit} messages for a user, ordered oldest-first
     * so the list can be fed directly into the LLM conversation history.
     */
    public List<ChatMessage> findRecentByUserId(Long userId, int limit) {
        String sql = """
                SELECT id, user_id, role, content, created_at
                FROM chat_messages
                WHERE user_id = :user_id
                ORDER BY created_at DESC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("limit", limit);
        List<ChatMessage> messages = jdbcTemplate.query(sql, params, ROW_MAPPER);
        return messages.reversed();
    }

    public int deleteAllByUserId(Long userId) {
        String sql = "DELETE FROM chat_messages WHERE user_id = :user_id";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("user_id", userId));
    }

    public ChatMessage saveCompactedSummary(Long userId, String summary) {
        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setRole("SYSTEM");
        message.setContent(summary);
        return save(message);
    }

    private static class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage msg = new ChatMessage();
            msg.setId(rs.getLong("id"));
            msg.setUserId(rs.getLong("user_id"));
            msg.setRole(rs.getString("role"));
            msg.setContent(rs.getString("content"));
            Timestamp ts = rs.getTimestamp("created_at");
            msg.setCreatedAt(ts != null ? ts.toInstant() : null);
            return msg;
        }
    }
}
