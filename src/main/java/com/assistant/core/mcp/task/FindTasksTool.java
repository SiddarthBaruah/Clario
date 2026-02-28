package com.assistant.core.mcp.task;

import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool: find_tasks. Search tasks by natural-language query (title/description).
 * Use when the user refers to a task by description or when resolving which task to act on.
 */
@Component
public class FindTasksTool implements Tool {

    private static final int DEFAULT_MAX = 10;

    private final TaskService taskService;

    public FindTasksTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "find_tasks";
    }

    @Override
    public String description() {
        return "Use when the user refers to a task by description (e.g. 'the milk task', 'call John', 'tomorrow's meeting') "
                + "or when you need to find which task they mean before acting. Returns tasks matching the query (id, title, description, status). "
                + "Call this for search-only (e.g. 'show tasks about X') or use resolve_and_act_on_task to find and perform an action in one step. "
                + "Parameters: userId (required), query (required, normalized task reference).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        String query = getQuery(arguments);
        int max = getMaxResults(arguments);
        List<TaskResponseDTO> tasks = taskService.searchTasksByQuery(userId, query, max);
        List<Map<String, Object>> items = tasks.stream().map(FindTasksTool::toMap).collect(Collectors.toList());
        return Map.of("tasks", items, "count", items.size());
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static String getQuery(Map<String, Object> args) {
        Object v = args.get("query");
        return v != null ? v.toString().trim() : "";
    }

    private static int getMaxResults(Map<String, Object> args) {
        Object v = args.get("maxResults");
        if (v instanceof Number n) return n.intValue() > 0 ? n.intValue() : DEFAULT_MAX;
        return DEFAULT_MAX;
    }

    static Map<String, Object> toMap(TaskResponseDTO dto) {
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
