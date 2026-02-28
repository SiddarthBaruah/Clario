package com.assistant.core.mcp;

import java.util.List;
import java.util.Map;

/**
 * Result of a single LLM call that supports both text reply and tool calls.
 * Used by the conversation loop: either content (final reply) or toolCalls (execute and continue).
 */
public sealed interface ChatWithToolsResult {

    /** Final text reply; no tool calls. */
    record Content(String text) implements ChatWithToolsResult {}

    /** One or more tool calls to execute; no final text yet. */
    record ToolCalls(List<SingleToolCall> calls) implements ChatWithToolsResult {}

    record SingleToolCall(String id, String name, Map<String, Object> arguments) {}
}
