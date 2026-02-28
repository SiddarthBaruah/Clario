package com.assistant.core.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps tool names from LLM response to registered Tool implementations.
 * Only predefined tools are invoked—never raw SQL or arbitrary execution.
 */
@Component
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    /** Allowed tool names; only these can be invoked from LLM output. */
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "create_task", "list_tasks", "find_tasks", "update_task_status", "delete_task", "resolve_and_act_on_task",
            "add_person", "retrieve_people"
    );

    private final List<Tool> tools;

    public ToolRouter(List<Tool> tools) {
        this.tools = tools;
    }

    public Optional<Tool> findTool(String name) {
        if (name == null || !ALLOWED_TOOLS.contains(name)) {
            return Optional.empty();
        }
        return tools.stream()
                .filter(t -> t.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<Tool> listTools() {
        return List.copyOf(tools);
    }

    /**
     * Invoke a tool by name with the given parameters. Only allowed tool names are accepted.
     */
    public Map<String, Object> invoke(String toolName, Map<String, Object> arguments) {
        Tool tool = findTool(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disallowed tool: " + toolName));
        log.debug("Invoking tool: {}", toolName);
        return tool.execute(arguments != null ? arguments : Map.of());
    }
}
