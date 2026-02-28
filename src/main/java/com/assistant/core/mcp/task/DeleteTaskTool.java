package com.assistant.core.mcp.task;

import com.assistant.core.mcp.Tool;
import com.assistant.core.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool: delete_task. Delete a task by id.
 * Use after find_tasks or resolve_and_act_on_task when the user has chosen a task (e.g. disambiguation).
 */
@Component
public class DeleteTaskTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DeleteTaskTool.class);

    private final TaskService taskService;

    public DeleteTaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "delete_task";
    }

    @Override
    public String description() {
        return "Use to delete (remove) a task. "
                + "Requires taskId from find_tasks or from a previous resolve_and_act_on_task disambiguation. "
                + "Call after find_tasks when the user has picked one (e.g. 'the first one') or when you already have the task id. "
                + "Parameters: userId (required), taskId (required).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        Long taskId = getTaskId(arguments);
        try {
            taskService.delete(userId, taskId);
            return Map.of("deleted", true, "taskId", taskId);
        } catch (IllegalArgumentException e) {
            log.warn("DeleteTaskTool: {}", e.getMessage());
            return Map.of("deleted", false, "error", e.getMessage());
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
}
