package com.assistant.core.service;

import com.assistant.core.mcp.ChatWithToolsResult;
import com.assistant.core.mcp.LLMService;
import com.assistant.core.mcp.ToolRouter;
import com.assistant.core.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Reusable conversation loop: load history, add user message, then loop: LLM with tools →
 * if tool_calls: execute each (dedupe by id), append assistant + tool result messages, persist as INTERNAL, repeat;
 * else persist final reply as USER_FACING and return. No channel-specific logic (e.g. WhatsApp).
 */
@Service
public class ConversationOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ConversationOrchestratorService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ITERATIONS = 5;
    private static final int HISTORY_LIMIT = 50;

    private final ChatMemoryService chatMemoryService;
    private final LLMService llmService;
    private final ToolRouter toolRouter;

    public ConversationOrchestratorService(ChatMemoryService chatMemoryService,
                                           LLMService llmService,
                                           ToolRouter toolRouter) {
        this.chatMemoryService = chatMemoryService;
        this.llmService = llmService;
        this.toolRouter = toolRouter;
    }

    /**
     * Processes one user message: persists it (USER_FACING), runs the loop until the model
     * returns a final text reply, persists that (USER_FACING), and returns the reply string.
     */
    public String processMessage(Long userId, String userMessage) {
        chatMemoryService.saveUserMessage(userId, userMessage);
        // History is oldest-first (API expects chronological order for correct turn-taking).
        List<Map<String, Object>> messages = new ArrayList<>(chatMemoryService.getConversationHistoryForContext(userId, HISTORY_LIMIT));

        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            ChatWithToolsResult result = llmService.chatWithTools(userId, messages);
            if (result instanceof ChatWithToolsResult.Content content) {
                String text = content.text();
                chatMemoryService.saveAssistantMessage(userId, text, ChatMessage.VISIBILITY_USER_FACING);
                return text;
            }
            if (result instanceof ChatWithToolsResult.ToolCalls toolCalls) {
                Set<String> processedIds = new HashSet<>();
                List<ChatWithToolsResult.SingleToolCall> calls = toolCalls.calls();
                List<Map<String, Object>> assistantToolCallsApi = new ArrayList<>();
                List<ChatWithToolsResult.SingleToolCall> toExecute = new ArrayList<>();
                for (ChatWithToolsResult.SingleToolCall call : calls) {
                    if (processedIds.contains(call.id())) {
                        log.warn("Skipping duplicate tool call id: {}", call.id());
                        continue;
                    }
                    processedIds.add(call.id());
                    assistantToolCallsApi.add(Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of("name", call.name(), "arguments", toJsonArgs(call.arguments()))
                    ));
                    toExecute.add(call);
                }
                messages.add(assistantMessageWithToolCalls(assistantToolCallsApi));
                chatMemoryService.saveAssistantMessage(userId, assistantContentJson(assistantToolCallsApi), ChatMessage.VISIBILITY_INTERNAL);
                for (ChatWithToolsResult.SingleToolCall call : toExecute) {
                    Map<String, Object> args = ensureUserId(call.arguments(), userId);
                    String resultStr;
                    try {
                        Map<String, Object> toolResult = toolRouter.invoke(call.name(), args);
                        resultStr = JSON.writeValueAsString(toolResult);
                    } catch (Exception e) {
                        log.error("Tool execution failed: {} - {}", call.name(), e.getMessage(), e);
                        resultStr = "Error: " + e.getMessage();
                    }
                    messages.add(toolMessage(call.id(), resultStr));
                    chatMemoryService.saveToolResultMessage(userId, call.id(), resultStr);
                }
                iteration++;
                continue;
            }
            iteration++;
        }
        String fallback = "I couldn't complete that in time. Please try again.";
        chatMemoryService.saveAssistantMessage(userId, fallback, ChatMessage.VISIBILITY_USER_FACING);
        return fallback;
    }

    private static String toJsonArgs(Map<String, Object> arguments) {
        try {
            return JSON.writeValueAsString(arguments != null ? arguments : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> ensureUserId(Map<String, Object> parameters, Long userId) {
        Map<String, Object> out = new LinkedHashMap<>(parameters != null ? parameters : Map.of());
        out.put("userId", userId);
        return out;
    }

    private static Map<String, Object> assistantMessageWithToolCalls(List<Map<String, Object>> toolCallsApi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("content", "");
        m.put("tool_calls", toolCallsApi);
        return m;
    }

    private static Map<String, Object> toolMessage(String toolCallId, String resultContent) {
        return Map.of("role", "tool", "content", resultContent, "tool_call_id", toolCallId);
    }

    private static String assistantContentJson(List<Map<String, Object>> toolCallsApi) {
        try {
            return JSON.writeValueAsString(Map.of("content", "", "tool_calls", toolCallsApi));
        } catch (Exception e) {
            return "{\"content\":\"\",\"tool_calls\":[]}";
        }
    }
}
