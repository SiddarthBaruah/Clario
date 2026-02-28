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
 * MCP tool: list_tasks. Delegates to TaskService only—no raw SQL.
 */
@Component
public class ListTasksTool implements Tool {

    private final TaskService taskService;

    public ListTasksTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "list_tasks";
    }

    @Override
    public String description() {
        return "Use when the user asks to see their tasks, to-dos, list, what they need to do, what's pending, or what they have scheduled (e.g. 'what are my tasks?', 'show my to-do list', 'what do I have due?'). "
                + "Returns active tasks (pending and in progress). Parameters: userId (required).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        List<TaskResponseDTO> tasks = taskService.listActiveTasks(userId);
        List<Map<String, Object>> items = tasks.stream()
                .map(ListTasksTool::toMap)
                .collect(Collectors.toList());
        return Map.of("tasks", items, "count", items.size());
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
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
