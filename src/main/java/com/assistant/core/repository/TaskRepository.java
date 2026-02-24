package com.assistant.core.repository;

import com.assistant.core.model.Task;
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
public class TaskRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Task> ROW_MAPPER = new TaskRowMapper();

    public TaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Task> findById(Long id) {
        String sql = "SELECT id, user_id, title, description, due_time, reminder_time, status, created_at FROM tasks WHERE id = :id";
        List<Task> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Task> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, title, description, due_time, reminder_time, status, created_at FROM tasks WHERE user_id = :user_id ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("user_id", userId), ROW_MAPPER);
    }

    public List<Task> findByUserIdAndStatus(Long userId, String status) {
        String sql = "SELECT id, user_id, title, description, due_time, reminder_time, status, created_at FROM tasks WHERE user_id = :user_id AND status = :status ORDER BY created_at DESC";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("status", status);
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public List<Task> findByUserIdAndStatus(Long userId, String status, int limit, int offset) {
        String sql = "SELECT id, user_id, title, description, due_time, reminder_time, status, created_at FROM tasks WHERE user_id = :user_id AND status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public long countByUserIdAndStatus(Long userId, String status) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE user_id = :user_id AND status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("status", status);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0;
    }

    public List<Task> findUpcomingReminders(Instant before) {
        String sql = "SELECT id, user_id, title, description, due_time, reminder_time, status, created_at FROM tasks WHERE reminder_time IS NOT NULL AND reminder_time <= :before AND status = :status ORDER BY reminder_time";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("before", Timestamp.from(before))
                .addValue("status", "PENDING");
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public Task save(Task task) {
        if (task.getId() == null) {
            return insert(task);
        }
        update(task);
        return task;
    }

    public int deleteById(Long id) {
        String sql = "DELETE FROM tasks WHERE id = :id";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    private Task insert(Task task) {
        String sql = "INSERT INTO tasks (user_id, title, description, due_time, reminder_time, status) VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, task.getUserId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            ps.setObject(4, task.getDueTime() != null ? Timestamp.from(task.getDueTime()) : null);
            ps.setObject(5, task.getReminderTime() != null ? Timestamp.from(task.getReminderTime()) : null);
            ps.setString(6, task.getStatus() != null ? task.getStatus() : "PENDING");
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id).orElseThrow();
    }

    private void update(Task task) {
        String sql = """
                UPDATE tasks SET user_id = :user_id, title = :title, description = :description,
                due_time = :due_time, reminder_time = :reminder_time, status = :status
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", task.getId())
                .addValue("user_id", task.getUserId())
                .addValue("title", task.getTitle())
                .addValue("description", task.getDescription())
                .addValue("due_time", task.getDueTime() != null ? Timestamp.from(task.getDueTime()) : null)
                .addValue("reminder_time", task.getReminderTime() != null ? Timestamp.from(task.getReminderTime()) : null)
                .addValue("status", task.getStatus());
        jdbcTemplate.update(sql, params);
    }

    private static class TaskRowMapper implements RowMapper<Task> {
        @Override
        public Task mapRow(ResultSet rs, int rowNum) throws SQLException {
            Task task = new Task();
            task.setId(rs.getLong("id"));
            task.setUserId(rs.getLong("user_id"));
            task.setTitle(rs.getString("title"));
            task.setDescription(rs.getString("description"));
            task.setDueTime(toInstant(rs.getTimestamp("due_time")));
            task.setReminderTime(toInstant(rs.getTimestamp("reminder_time")));
            task.setStatus(rs.getString("status"));
            task.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            return task;
        }

        private static Instant toInstant(java.sql.Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
