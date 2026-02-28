package com.assistant.core.mcp.task;

import com.assistant.core.dto.TaskResponseDTO;
import com.assistant.core.mcp.Tool;
import com.assistant.core.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool: resolve_and_act_on_task. Find task(s) by user description and perform one action when exactly one match.
 * Use when the user says e.g. "delete the milk task", "mark call John as done", "set the meeting to in progress".
 * If one task matches, the action is performed. If 0 or many match, no action; return message or candidates for disambiguation.
 */
@Component
public class ResolveAndActOnTaskTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ResolveAndActOnTaskTool.class);

    private static final int MAX_CANDIDATES = 10;

    private final TaskService taskService;

    public ResolveAndActOnTaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() {
        return "resolve_and_act_on_task";
    }

    @Override
    public String description() {
        return "Use when the user wants to delete a task, mark it done, mark it pending, or mark it in progress "
                + "and refers to the task by description (e.g. 'remove the milk task', 'mark call John as done', 'set the meeting to in progress'). "
                + "Extract a normalized task reference (e.g. 'milk', 'call John', 'meeting') as userDescription. "
                + "Action must be one of: delete, mark_done, mark_pending, mark_in_progress. "
                + "If exactly one task matches, the action is performed. If 0 match, reply that no task was found. "
                + "If multiple match, return candidates and ask the user which one (do not auto-pick). "
                + "Parameters: userId (required), userDescription (required), action (required).";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long userId = getUserId(arguments);
        String userDescription = getUserDescription(arguments);
        String action = getAction(arguments);

        if (userDescription == null || userDescription.isBlank()) {
            return Map.of("resolved", false, "message", "No task description provided.");
        }

        List<TaskResponseDTO> candidates = taskService.searchTasksByQuery(userId, userDescription, MAX_CANDIDATES);

        if (candidates.isEmpty()) {
            return Map.of("resolved", false, "message", "No matching task found. Suggest the user list their tasks or rephrase.");
        }

        if (candidates.size() > 1) {
            List<Map<String, Object>> list = candidates.stream().map(FindTasksTool::toMap).collect(Collectors.toList());
            return Map.of(
                    "resolved", false,
                    "ambiguous", true,
                    "message", "Multiple tasks match; ask the user which one (e.g. by number or more specific description).",
                    "candidates", list
            );
        }

        TaskResponseDTO task = candidates.get(0);
        Long taskId = task.getId();

        try {
            switch (action.toLowerCase()) {
                case "delete" -> {
                    taskService.delete(userId, taskId);
                    return Map.of(
                            "resolved", true,
                            "action", "delete",
                            "task", FindTasksTool.toMap(task),
                            "message", "Task deleted: " + task.getTitle()
                    );
                }
                case "mark_done" -> {
                    TaskResponseDTO updated = taskService.updateStatus(userId, taskId, "DONE");
                    return Map.of(
                            "resolved", true,
                            "action", "mark_done",
                            "task", FindTasksTool.toMap(updated),
                            "message", "Marked as done: " + updated.getTitle()
                    );
                }
                case "mark_pending" -> {
                    TaskResponseDTO updated = taskService.updateStatus(userId, taskId, "PENDING");
                    return Map.of(
                            "resolved", true,
                            "action", "mark_pending",
                            "task", FindTasksTool.toMap(updated),
                            "message", "Marked as pending: " + updated.getTitle()
                    );
                }
                case "mark_in_progress" -> {
                    TaskResponseDTO updated = taskService.updateStatus(userId, taskId, "IN_PROGRESS");
                    return Map.of(
                            "resolved", true,
                            "action", "mark_in_progress",
                            "task", FindTasksTool.toMap(updated),
                            "message", "Marked in progress: " + updated.getTitle()
                    );
                }
                default -> {
                    return Map.of("resolved", false, "message", "Unknown action: " + action);
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("ResolveAndActOnTaskTool: {}", e.getMessage());
            return Map.of("resolved", false, "message", e.getMessage());
        }
    }

    private static Long getUserId(Map<String, Object> args) {
        Object v = args.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("userId is required");
    }

    private static String getUserDescription(Map<String, Object> args) {
        Object v = args.get("userDescription");
        return v != null ? v.toString().trim() : "";
    }

    private static String getAction(Map<String, Object> args) {
        Object v = args.get("action");
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("action is required");
        return v.toString().trim();
    }
}
