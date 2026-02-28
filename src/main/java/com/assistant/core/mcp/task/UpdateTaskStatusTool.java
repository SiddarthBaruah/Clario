package com.assistant.core.mcp.task;

import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool: update_task_status. Set a task's status to PENDING, IN_PROGRESS, or DONE.
 * Use after find_tasks or resolve_and_act_on_task when the user has chosen a task (e.g. disambiguation).
 */
@Component
public class UpdateTaskStatusTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(UpdateTaskStatusTool.class);

    private final TaskService taskService;

    public UpdateTaskStatusTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "update_task_status";
    }

    @Override
    public String description() {
        return "Use to set a task's status to PENDING, IN_PROGRESS, or DONE. "
                + "Requires taskId from find_tasks or from a previous resolve_and_act_on_task disambiguation. "
                + "Call after find_tasks when the user has picked one (e.g. 'the first one') or when you already have the task id. "
                + "Parameters: userId (required), taskId (required), status (required: PENDING | IN_PROGRESS | DONE).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        Long taskId = getTaskId(arguments);
        String status = getStatus(arguments);
        try {
            TaskResponseDTO task = taskService.updateStatus(userId, taskId, status);
            return Map.of("success", true, "task", FindTasksTool.toMap(task));
        } catch (IllegalArgumentException e) {
            log.warn("UpdateTaskStatusTool: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static Long getTaskId(Map<String, Object> args) {
        Object v = args.get("taskId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("taskId is required");
    }

    private static String getStatus(Map<String, Object> args) {
        Object v = args.get("status");
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("status is required");
        return v.toString().trim().toUpperCase();
    }
}
