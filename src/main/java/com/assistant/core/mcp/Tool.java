package com.assistant.core.mcp;

import java.util.Map;

/**
 * Interface for MCP (Model Context Protocol) tools that can be invoked by the LLM integration.
 */
public interface Tool {

    String name();

    String description();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments tool-specific arguments
     * @return result as a map (e.g. for JSON serialization)
     */
    Map<String, Object> execute(Map<String, Object> arguments);
}
