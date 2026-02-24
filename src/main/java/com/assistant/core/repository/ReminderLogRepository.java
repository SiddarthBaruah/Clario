package com.assistant.core.repository;

import com.assistant.core.model.ReminderLog;
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
public class ReminderLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<ReminderLog> ROW_MAPPER = new ReminderLogRowMapper();

    public ReminderLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByTaskId(Long taskId) {
        String sql = "SELECT id, task_id, sent_at, status FROM reminder_log WHERE task_id = :task_id LIMIT 1";
        List<ReminderLog> list = jdbcTemplate.query(sql, new MapSqlParameterSource("task_id", taskId), ROW_MAPPER);
        return !list.isEmpty();
    }

    public ReminderLog insert(ReminderLog log) {
        String sql = "INSERT INTO reminder_log (task_id, sent_at, status) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, log.getTaskId());
            ps.setObject(2, log.getSentAt() != null ? Timestamp.from(log.getSentAt()) : null);
            ps.setString(3, log.getStatus() != null ? log.getStatus() : "SENT");
            return ps;
        }, keyHolder);
        log.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        return log;
    }

    private static class ReminderLogRowMapper implements RowMapper<ReminderLog> {
        @Override
        public ReminderLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReminderLog log = new ReminderLog();
            log.setId(rs.getLong("id"));
            log.setTaskId(rs.getLong("task_id"));
            log.setSentAt(toInstant(rs.getTimestamp("sent_at")));
            log.setStatus(rs.getString("status"));
            return log;
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
