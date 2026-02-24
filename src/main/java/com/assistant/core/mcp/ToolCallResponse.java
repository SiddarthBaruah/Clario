package com.assistant.core.mcp;

import java.util.Map;

/**
 * Structured response from LLMService representing which tool to call and with what parameters.
 * No raw SQL or arbitrary execution is allowed—only predefined tools are invoked.
 */
public record ToolCallResponse(String tool, Map<String, Object> parameters) {

    public ToolCallResponse {
        parameters = parameters != null ? parameters : Map.of();
    }
}
