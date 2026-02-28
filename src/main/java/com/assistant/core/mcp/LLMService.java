package com.assistant.core.mcp;

import com.assistant.core.service.AssistantProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI (or compatible) API integration using Spring Boot 3 RestClient.
 * Uses native tool/function calling when the API returns tool_calls; falls back to parsing
 * JSON from message content for older or non-standard responses.
 * Returns strictly structured tool name + parameters; only tools mapped by ToolRouter are invoked.
 */
@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Returned when the LLM is unavailable or response cannot be parsed, so the loop exits with a message instead of re-calling tools. */
    private static final String UNAVAILABLE_MESSAGE = "Sorry, I had trouble processing that. Please try again.";

    private final RestClient restClient;
    private final AssistantProfileService assistantProfileService;
    private final ToolRouter toolRouter;
    private final String baseUrl;
    private final String apiKey;

    public LLMService(@Value("${app.llm.base-url:}") String baseUrl,
                      @Value("${app.llm.api-key:}") String apiKey,
                      AssistantProfileService assistantProfileService,
                      ToolRouter toolRouter) {
        this.assistantProfileService = assistantProfileService;
        this.toolRouter = toolRouter;
        this.baseUrl = baseUrl != null ? baseUrl.strip() : "";
        this.apiKey = apiKey != null ? apiKey : "";
        this.restClient = this.baseUrl.isBlank()
                ? RestClient.create("http://placeholder")
                : RestClient.builder()
                        .baseUrl(this.baseUrl)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader("Authorization", "Bearer " + this.apiKey)
                        .build();
    }

    /**
     * Returns the system context (personality prompt) for the given user for use in LLM requests.
     */
    public String getSystemContextForUser(Long userId) {
        return assistantProfileService.getSystemContextPrompt(userId);
    }

    /**
     * Calls the LLM via the Responses API (/v1/responses) with tool definitions.
     * gpt-5.1-codex-mini only supports this endpoint, not /v1/chat/completions.
     * Returns which tool to call and its parameters.
     */
    public ToolCallResponse requestToolCall(Long userId, String userMessage, String conversationHistory) {
        if (this.baseUrl.isBlank()) {
            log.debug("LLM base URL not configured; returning placeholder tool call");
            return placeholderResponse(userId);
        }
        String systemContext = getSystemContextForUser(userId);
        String currentTimeContext = "\n\n--- Current time (use this to resolve relative times like 'tomorrow at 3pm', 'next Friday', 'in 2 hours') ---\n"
                + "Current date and time in ISO-8601 (UTC): " + Instant.now().atOffset(ZoneOffset.UTC).toString()
                + "\nAlways output dueTime and reminderTime as full ISO-8601 timestamps (e.g. 2025-02-24T15:00:00Z). Never use placeholders or incomplete values like 2024-10-??? or 2024-???.";
        systemContext += currentTimeContext;
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            systemContext += "\n\n--- Conversation History ---\n" + conversationHistory;
        }
        List<Map<String, Object>> tools = buildToolDefinitions();
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "gpt-5.1-codex-mini");
            requestBody.put("instructions", systemContext);
            requestBody.put("input", List.of(
                    Map.of("role", "user", "content", userMessage)
            ));
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "required");

            String responseBody = restClient.post()
                    .uri("/v1/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return placeholderResponse(userId);
            }

            Map<String, Object> top = parseJsonToMap(responseBody);
            if (top == null) {
                return placeholderResponse(userId);
            }

            ToolCallResponse fromOutput = parseFunctionCallFromOutput(top);
            if (fromOutput != null) {
                return fromOutput;
            }
            return placeholderResponse(userId);
        } catch (Exception e) {
            log.warn("LLM request failed, returning placeholder: {}", e.getMessage());
            return placeholderResponse(userId);
        }
    }

    /**
     * Multi-turn conversation with tools. Sends full message list; returns either final text content
     * or a list of tool calls (possibly multiple). Used by the conversation loop.
     * Replaces the single-shot requestToolCall + generateNaturalResponse flow for the loop path.
     */
    public ChatWithToolsResult chatWithTools(Long userId, List<Map<String, Object>> messages) {
        if (this.baseUrl.isBlank()) {
            log.warn("LLM base URL not configured; returning user-facing message so loop exits. baseUrl is blank.");
            return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
        }
        String systemContext = getSystemContextForUser(userId);
        String currentTimeContext = "\n\n--- Current time (use this to resolve relative times like 'tomorrow at 3pm', 'next Friday', 'in 2 hours') ---\n"
                + "Current date and time in ISO-8601 (UTC): " + Instant.now().atOffset(ZoneOffset.UTC)
                + "\nAlways output dueTime and reminderTime as full ISO-8601 timestamps (e.g. 2025-02-24T15:00:00Z). Never use placeholders or incomplete values.";
        systemContext += currentTimeContext;
        systemContext += "\n\nAfter receiving any tool result, respond to the user in natural language. Do not call the same tool again without a new explicit user request.";

        List<Map<String, Object>> input = buildInputForResponsesApi(messages);
        List<Map<String, Object>> tools = buildToolDefinitions();
        // Require tool use when the user just sent a message (no tool results in this turn yet)
        boolean lastMessageIsUser = lastInputItemIsUserMessage(input);
        String toolChoice = lastMessageIsUser ? "required" : "auto";
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "gpt-5.1-codex-mini");
            requestBody.put("instructions", systemContext);
            requestBody.put("input", input);
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", toolChoice);

            String responseBody = restClient.post()
                    .uri("/v1/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("chatWithTools: empty API response body; returning user-facing message. responseBody is null or blank.");
                return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
            }
            Map<String, Object> top = parseJsonToMap(responseBody);
            if (top == null) {
                log.warn("chatWithTools: API response is not valid JSON; returning user-facing message. Raw response (truncated): {}", truncate(responseBody, 500));
                return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
            }
            return parseChatWithToolsOutput(top, userId, responseBody);
        } catch (Exception e) {
            log.warn("chatWithTools failed; returning user-facing message. Error: {}", e.getMessage());
            return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
        }
    }

    private static boolean lastInputItemIsUserMessage(List<Map<String, Object>> input) {
        if (input == null || input.isEmpty()) return false;
        Object last = input.get(input.size() - 1);
        if (!(last instanceof Map<?, ?> m)) return false;
        return "user".equals(m.get("role"));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Build input array for /v1/responses from our message list (user/assistant/tool).
     * Responses API expects: user/assistant messages with role+content; assistant tool turns as
     * input items type "function_call" (call_id, name, arguments); tool results as
     * type "function_call_output" (call_id, output). API expects oldest-first order.
     */
    private List<Map<String, Object>> buildInputForResponsesApi(List<Map<String, Object>> messages) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            String role = Objects.toString(m.get("role"), "user");
            Object content = m.get("content");
            String contentStr = content != null ? content.toString() : "";
            if ("user".equals(role)) {
                input.add(Map.of("role", "user", "content", contentStr));
            } else if ("assistant".equals(role)) {
                Object toolCalls = m.get("tool_calls");
                if (toolCalls instanceof List<?> list && !list.isEmpty()) {
                    for (Object tc : list) {
                        if (!(tc instanceof Map<?, ?> tcm)) continue;
                        String callId = tcm.get("id") != null ? tcm.get("id").toString() : null;
                        Object fn = tcm.get("function");
                        String name = null;
                        String argumentsStr = "{}";
                        if (fn instanceof Map<?, ?> fnMap) {
                            name = fnMap.get("name") != null ? fnMap.get("name").toString() : null;
                            Object args = fnMap.get("arguments");
                            if (args != null) {
                                try {
                                argumentsStr = args instanceof String ? (String) args : OBJECT_MAPPER.writeValueAsString(args);
                            } catch (Exception e) {
                                argumentsStr = "{}";
                            }
                            }
                        }
                        if (callId != null && name != null) {
                            input.add(Map.of("type", "function_call", "call_id", callId, "name", name, "arguments", argumentsStr));
                        }
                    }
                } else {
                    input.add(Map.of("role", "assistant", "content", contentStr));
                }
            } else if ("tool".equals(role) || "function".equals(role)) {
                String toolCallId = m.get("tool_call_id") != null ? m.get("tool_call_id").toString() : m.get("name") != null ? m.get("name").toString() : "call";
                input.add(Map.of("type", "function_call_output", "call_id", toolCallId, "output", contentStr));
            }
            // skip system or unknown
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private ChatWithToolsResult parseChatWithToolsOutput(Map<String, Object> response, Long userId, String rawResponseBody) {
        Object output = response.get("output");
        if (!(output instanceof List<?> outputList) || outputList.isEmpty()) {
            log.warn("parseChatWithToolsOutput: response.output missing, not a list, or empty. Raw output (truncated): {}", truncate(rawResponseBody, 500));
            return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
        }
        List<ChatWithToolsResult.SingleToolCall> toolCalls = new ArrayList<>();
        String textContent = null;
        int callIndex = 0;
        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            String type = entry.get("type") != null ? entry.get("type").toString() : "";
            if ("function_call".equals(type)) {
                String name = entry.get("name") != null ? entry.get("name").toString() : null;
                if (name != null && !name.isBlank()) {
                    Map<String, Object> params = Map.of();
                    Object argsObj = entry.get("arguments");
                    if (argsObj != null) {
                        if (argsObj instanceof Map) {
                            params = (Map<String, Object>) argsObj;
                        } else if (!argsObj.toString().isBlank()) {
                            Map<String, Object> parsed = parseJsonToMap(argsObj.toString());
                            if (parsed != null) params = parsed;
                        }
                    }
                    String id = entry.get("call_id") != null ? entry.get("call_id").toString() : entry.get("id") != null ? entry.get("id").toString() : ("call_" + callIndex++);
                    toolCalls.add(new ChatWithToolsResult.SingleToolCall(id, name, params));
                }
            } else if ("message".equals(type)) {
                Object content = entry.get("content");
                if (content != null && !content.toString().isBlank()) {
                    textContent = extractTextFromMessageContent(content);
                }
            } else if ("output_text".equals(type)) {
                Object text = entry.get("text");
                if (text != null && !text.toString().isBlank()) {
                    textContent = text.toString().strip();
                }
            }
        }
        if (!toolCalls.isEmpty()) {
            return new ChatWithToolsResult.ToolCalls(toolCalls);
        }
        if (textContent != null && !textContent.isBlank()) {
            return new ChatWithToolsResult.Content(textContent);
        }
        log.warn("parseChatWithToolsOutput: no recognized tool_calls or text in output; returning user-facing message. response.output: {}", output);
        return new ChatWithToolsResult.Content(UNAVAILABLE_MESSAGE);
    }

    /** Extract plain text from message content (may be string or array of content parts, e.g. output_text). */
    private static String extractTextFromMessageContent(Object content) {
        if (content == null) return null;
        if (content instanceof String s) return s.isBlank() ? null : s.strip();
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object part : list) {
                if (part instanceof Map<?, ?> m) {
                    Object text = m.get("text");
                    if (text != null && !text.toString().isBlank()) sb.append(text.toString().strip()).append(" ");
                }
            }
            String t = sb.toString().strip();
            return t.isEmpty() ? null : t;
        }
        return content.toString().strip();
    }

    /**
     * Parse the Responses API output array for a function_call item.
     * Output items look like: { "type": "function_call", "name": "create_task", "arguments": "{...}" }
     * @deprecated Prefer {@link #chatWithTools(Long, List)} for the multi-turn loop.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private static ToolCallResponse parseFunctionCallFromOutput(Map<String, Object> response) {
        Object output = response.get("output");
        if (!(output instanceof List<?> outputList) || outputList.isEmpty()) return null;

        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            if (!"function_call".equals(entry.get("type"))) continue;

            String name = entry.get("name") != null ? entry.get("name").toString() : null;
            if (name == null || name.isBlank()) continue;

            Map<String, Object> params = Map.of();
            Object argsObj = entry.get("arguments");
            if (argsObj != null) {
                if (argsObj instanceof Map) {
                    params = (Map<String, Object>) argsObj;
                } else if (!argsObj.toString().isBlank()) {
                    Map<String, Object> parsed = parseJsonToMap(argsObj.toString());
                    if (parsed != null) params = parsed;
                }
            }
            return new ToolCallResponse(name, params);
        }
        return null;
    }

    /** Build tools array for /v1/responses — flat format: { type, name, description, parameters }. */
    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tool t : toolRouter.listTools()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            def.put("name", t.name());
            def.put("description", t.description());
            def.put("parameters", toolParametersSchema(t.name()));
            out.add(def);
        }
        return out;
    }

    /** JSON schema for each tool's parameters (userId injected by caller). */
    private static Map<String, Object> toolParametersSchema(String toolName) {
        return switch (toolName) {
            case "create_task" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "title", Map.of("type", "string", "description", "Task title"),
                            "description", Map.of("type", "string", "description", "Task description"),
                            "dueTime", Map.of("type", "string", "description",
                                    "Due date/time as full ISO-8601 only (e.g. 2025-02-24T15:00:00Z). Resolve relative phrases like 'tomorrow at 3pm' using the current time from context. Omit if not specified."),
                            "reminderTime", Map.of("type", "string", "description",
                                    "Reminder date/time as full ISO-8601 only (e.g. 2025-02-24T14:45:00Z). Resolve using current time from context. Omit if not specified.")
                    ),
                    "required", List.of("userId", "title")
            );
            case "list_tasks" -> Map.of(
                    "type", "object",
                    "properties", Map.of("userId", Map.of("type", "number", "description", "User ID")),
                    "required", List.of("userId")
            );
            case "find_tasks" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "query", Map.of("type", "string", "description", "Normalized task reference (e.g. 'milk', 'call John')"),
                            "maxResults", Map.of("type", "number", "description", "Max tasks to return (optional, default 10)")
                    ),
                    "required", List.of("userId", "query")
            );
            case "update_task_status" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "taskId", Map.of("type", "number", "description", "Task ID from find_tasks or disambiguation"),
                            "status", Map.of("type", "string", "description", "PENDING, IN_PROGRESS, or DONE")
                    ),
                    "required", List.of("userId", "taskId", "status")
            );
            case "delete_task" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "taskId", Map.of("type", "number", "description", "Task ID from find_tasks or disambiguation")
                    ),
                    "required", List.of("userId", "taskId")
            );
            case "resolve_and_act_on_task" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "userDescription", Map.of("type", "string", "description", "Normalized task reference (e.g. 'milk', 'call John')"),
                            "action", Map.of("type", "string", "description", "delete, mark_done, mark_pending, or mark_in_progress")
                    ),
                    "required", List.of("userId", "userDescription", "action")
            );
            case "add_person" -> Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "number", "description", "User ID"),
                            "name", Map.of("type", "string", "description", "Contact name"),
                            "notes", Map.of("type", "string", "description", "Notes"),
                            "importantDates", Map.of("type", "string", "description", "Important dates JSON string")
                    ),
                    "required", List.of("userId", "name")
            );
            case "retrieve_people" -> Map.of(
                    "type", "object",
                    "properties", Map.of("userId", Map.of("type", "number", "description", "User ID")),
                    "required", List.of("userId")
            );
            default -> Map.of("type", "object", "properties", Map.of("userId", Map.of("type", "number", "description", "User ID")), "required", List.of("userId"));
        };
    }

    /** Extract choices[0].message from Chat Completions response. */
    private static Map<String, Object> extractMessage(Map<String, Object> response) {
        if (response == null) return null;
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map)) return null;
        Object msg = ((Map<?, ?>) first).get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap = msg instanceof Map ? (Map<String, Object>) msg : null;
        return messageMap;
    }

    /**
     * Second LLM pass: given the original user message, the tool that was invoked, and the raw tool
     * result, produces a natural-language reply infused with the user's personality prompt (soul script).
     * Falls back to a plain JSON dump when the LLM is unreachable.
     */
    public String generateNaturalResponse(Long userId, String userMessage, String toolName,
                                          Map<String, Object> toolResult, String conversationHistory) {
        String systemContext = getSystemContextForUser(userId);
        String toolResultJson;
        try {
            toolResultJson = OBJECT_MAPPER.writeValueAsString(toolResult);
        } catch (Exception e) {
            toolResultJson = toolResult.toString();
        }

        if (this.baseUrl.isBlank()) {
            log.debug("LLM base URL not configured; returning raw tool result as response");
            return fallbackFormat(toolName, toolResultJson);
        }

        String historyBlock = "";
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            historyBlock = "\n\n--- Conversation History ---\n" + conversationHistory + "\n";
        }

        String responseSystemPrompt = systemContext + historyBlock + "\n\n"
                + "You just executed a tool on behalf of the user. "
                + "Summarize the result below in a warm, concise, natural-language message. "
                + "Do NOT mention tool names, JSON, or technical details. "
                + "Respond as if you are chatting with a friend on WhatsApp — keep it short and helpful.";

        String toolContext = "The user said: \"" + userMessage + "\"\n"
                + "Tool executed: " + toolName + "\n"
                + "Result:\n" + toolResultJson;

        try {
            Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
            requestBody.put("model", "gpt-4.1-nano");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", responseSystemPrompt),
                    Map.of("role", "user", "content", toolContext)
            ));

            String responseBody = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return fallbackFormat(toolName, toolResultJson);
            }

            Map<String, Object> top = parseJsonToMap(responseBody);
            Map<String, Object> message = extractMessage(top);
            if (message == null) {
                return fallbackFormat(toolName, toolResultJson);
            }

            Object content = message.get("content");
            if (content != null && !content.toString().isBlank()) {
                return content.toString().strip();
            }
            return fallbackFormat(toolName, toolResultJson);
        } catch (Exception e) {
            log.warn("Natural-response LLM call failed, using fallback: {}", e.getMessage());
            return fallbackFormat(toolName, toolResultJson);
        }
    }

    /**
     * General-purpose LLM call: sends a system prompt + user message and returns the
     * assistant's text reply.  Used for tasks like conversation compaction where no
     * tool calling is needed.
     */
    public String chat(String systemPrompt, String userMessage) {
        if (this.baseUrl.isBlank()) {
            log.debug("LLM base URL not configured; returning user message as-is");
            return userMessage;
        }
        try {
            Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
            requestBody.put("model", "gpt-4.1-nano");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));

            String responseBody = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return userMessage;
            }

            Map<String, Object> top = parseJsonToMap(responseBody);
            Map<String, Object> message = extractMessage(top);
            if (message != null) {
                Object content = message.get("content");
                if (content != null && !content.toString().isBlank()) {
                    return content.toString().strip();
                }
            }
            return userMessage;
        } catch (Exception e) {
            log.warn("LLM chat call failed, returning input as fallback: {}", e.getMessage());
            return userMessage;
        }
    }

    private static String fallbackFormat(String toolName, String resultJson) {
        return "Here's what I found (" + toolName + "):\n" + resultJson;
    }

    /**
     * Placeholder when LLM is not configured or request fails. Returns a safe default (list_tasks).
     */
    private ToolCallResponse placeholderResponse(Long userId) {
        return new ToolCallResponse("list_tasks", Map.of("userId", userId));
    }

    private static Map<String, Object> parseJsonToMap(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
