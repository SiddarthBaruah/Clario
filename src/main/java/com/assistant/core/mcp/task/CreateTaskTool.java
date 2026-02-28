package com.assistant.core.mcp.task;

import com.assistant.core.dto.TaskRequestDTO;
import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool: create_task. Delegates to TaskService only—no raw SQL.
 */
@Component
public class CreateTaskTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskTool.class);

    private final TaskService taskService;

    public CreateTaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "create_task";
    }

    @Override
    public String description() {
        return "Use when the user wants to add, create, or save a task, to-do, or reminder (e.g. 'add task buy milk', 'remind me to call John', 'I have a meet tomorrow at 3pm'). "
                + "Creates a new task. Parameters: userId (required), title (required), description (optional), dueTime (full ISO-8601 only, e.g. 2025-02-24T15:00:00Z — resolve 'tomorrow at 3pm' using current time from context; omit if not specified), reminderTime (full ISO-8601 only; omit if not specified).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        try {
            Long userId = getUserId(arguments);
            TaskRequestDTO request = new TaskRequestDTO();
            request.setTitle(requireString(arguments, "title"));
            request.setDescription(getString(arguments, "description"));
            request.setDueTime(parseInstantOrNull(arguments, "dueTime"));
            request.setReminderTime(parseInstantOrNull(arguments, "reminderTime"));
            TaskResponseDTO result = taskService.createTask(userId, request);
            return toMap(result);
        } catch (IllegalArgumentException e) {
            log.warn("CreateTaskTool validation error: {}", e.getMessage());
            return errorMap("validation_error", e.getMessage());
        } catch (Exception e) {
            log.error("CreateTaskTool failed", e);
            return errorMap("error", "Could not create task: " + e.getMessage());
        }
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static String requireString(Map<String, Object> args, String key) {
        String s = getString(args, key);
        if (s == null || s.isBlank()) throw new IllegalArgumentException(key + " is required");
        return s;
    }

    private static String getString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Parses dueTime/reminderTime from arguments. Returns null if missing, invalid, or placeholder
     * (e.g. "2024-???" or "2024-10-???"), so the task is still created without that field.
     */
    private static Instant parseInstantOrNull(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        String s = v.toString().trim();
        if (s.isBlank()) return null;
        if (isInvalidOrPlaceholderDate(s)) {
            log.debug("Skipping invalid or placeholder date for {}: '{}'", key, s);
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e1) {
            try {
                // No timezone (e.g. "2024-10-21T09:00:00") — treat as local time
                LocalDateTime ldt = LocalDateTime.parse(s);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    /** Rejects obviously invalid or placeholder date strings (e.g. "2024-???", "2024-10-???"). */
    private static boolean isInvalidOrPlaceholderDate(String s) {
        if (s == null || s.length() < 10) return true;
        if (s.contains("?") || s.contains("*") || s.contains("_")) return true;
        // Must look like a date: at least YYYY-MM-DD
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return true;
        for (int i = 0; i < Math.min(10, s.length()); i++) {
            char c = s.charAt(i);
            if (i == 4 || i == 7) continue;
            if (c < '0' || c > '9') return true;
        }
        return false;
    }

    private static Map<String, Object> errorMap(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    private static Map<String, Object> toMap(TaskResponseDTO dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.getId());
        m.put("userId", dto.getUserId());
        m.put("title", dto.getTitle());
        m.put("description", dto.getDescription());
        m.put("dueTime", dto.getDueTime() != null ? dto.getDueTime().toString() : null);
        m.put("reminderTime", dto.getReminderTime() != null ? dto.getReminderTime().toString() : null);
        m.put("status", dto.getStatus());
        m.put("createdAt", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        return m;
    }
}
