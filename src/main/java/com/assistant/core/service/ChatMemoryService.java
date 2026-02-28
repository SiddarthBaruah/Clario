package com.assistant.core.service;

import com.assistant.core.mcp.LLMService;
import com.assistant.core.model.ChatMessage;
import com.assistant.core.repository.ChatMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    private static final String COMPACTION_PROMPT =
            "Summarize this conversation history into a highly concise context block. "
            + "Retain key facts, pending tasks, and user preferences. "
            + "Drop pleasantries and filler. Output only the summary, nothing else.";

    private final ChatMessageRepository chatMessageRepository;
    private final LLMService llmService;

    public ChatMemoryService(ChatMessageRepository chatMessageRepository, LLMService llmService) {
        this.chatMessageRepository = chatMessageRepository;
        this.llmService = llmService;
    }

    public ChatMessage saveUserMessage(Long userId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole("USER");
        msg.setContent(content);
        msg.setVisibility(ChatMessage.VISIBILITY_USER_FACING);
        return chatMessageRepository.save(msg);
    }

    /** Saves an assistant message with the given visibility (USER_FACING for final reply, INTERNAL for tool-call turns). */
    public ChatMessage saveAssistantMessage(Long userId, String content, String visibility) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        msg.setVisibility(visibility != null ? visibility : ChatMessage.VISIBILITY_USER_FACING);
        return chatMessageRepository.save(msg);
    }

    /** Saves the final assistant reply shown to the user (USER_FACING). */
    public ChatMessage saveAssistantMessage(Long userId, String content) {
        return saveAssistantMessage(userId, content, ChatMessage.VISIBILITY_USER_FACING);
    }

    /** Saves a tool result message (INTERNAL only). Stored as JSON so tool_call_id is available when loading. */
    public ChatMessage saveToolResultMessage(Long userId, String toolCallId, String resultContent) {
        String content;
        try {
            content = JSON.writeValueAsString(Map.of("tool_call_id", toolCallId, "result", resultContent != null ? resultContent : ""));
        } catch (Exception e) {
            content = "{\"tool_call_id\":\"" + toolCallId + "\",\"result\":\"" + (resultContent != null ? resultContent.replace("\"", "\\\"") : "") + "\"}";
        }
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole("TOOL");
        msg.setContent(content);
        msg.setVisibility(ChatMessage.VISIBILITY_INTERNAL);
        return chatMessageRepository.save(msg);
    }

    /**
     * Returns the recent conversation history for a user as a single formatted string
     * suitable for injection into an LLM prompt (legacy / backward compatibility).
     */
    public String getConversationHistory(Long userId) {
        List<ChatMessage> messages = chatMessageRepository.findRecentByUserId(userId, DEFAULT_HISTORY_LIMIT);
        if (messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns full conversation history as a list of message maps in API shape (role, content, optional tool_calls / tool_call_id)
     * for use by the conversation loop (LLM context). Order: oldest first.
     */
    public List<Map<String, Object>> getConversationHistoryForContext(Long userId, int limit) {
        List<ChatMessage> rows = chatMessageRepository.findRecentByUserId(userId, limit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : rows) {
            String role = m.getRole();
            String content = m.getContent();
            if (content == null) content = "";
            switch (role) {
                case "USER" -> out.add(Map.of("role", "user", "content", content));
                case "SYSTEM" -> out.add(Map.of("role", "system", "content", content));
                case "ASSISTANT" -> {
                    Map<String, Object> assistantMsg = toAssistantMessageMap(content);
                    out.add(assistantMsg);
                }
                case "TOOL" -> {
                    Map<String, Object> toolMsg = toToolMessageMap(content);
                    if (toolMsg != null) out.add(toolMsg);
                }
                default -> out.add(Map.of("role", "assistant", "content", content));
            }
        }
        return out;
    }

    /**
     * Returns only user-facing messages for chat UI / display. Order: oldest first.
     */
    public List<ChatMessage> getUserFacingHistory(Long userId, int limit) {
        return chatMessageRepository.findUserFacingByUserId(userId, limit);
    }

    private static Map<String, Object> toAssistantMessageMap(String content) {
        if (content == null || !content.strip().startsWith("{")) {
            return new LinkedHashMap<>(Map.of("role", "assistant", "content", content != null ? content : ""));
        }
        try {
            Map<String, Object> parsed = JSON.readValue(content, new TypeReference<>() {});
            Object toolCalls = parsed.get("tool_calls");
            if (toolCalls instanceof List<?> list && !list.isEmpty()) {
                LinkedHashMap<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "assistant");
                msg.put("content", parsed.getOrDefault("content", "").toString());
                msg.put("tool_calls", toolCalls);
                return msg;
            }
        } catch (Exception ignored) { }
        return new LinkedHashMap<>(Map.of("role", "assistant", "content", content));
    }

    private static Map<String, Object> toToolMessageMap(String content) {
        if (content == null || !content.strip().startsWith("{")) {
            return Map.of("role", "tool", "content", content != null ? content : "", "tool_call_id", "legacy");
        }
        try {
            Map<String, Object> parsed = JSON.readValue(content, new TypeReference<>() {});
            String id = parsed.get("tool_call_id") != null ? parsed.get("tool_call_id").toString() : "legacy";
            String result = parsed.get("result") != null ? parsed.get("result").toString() : content;
            return Map.of("role", "tool", "content", result, "tool_call_id", id);
        } catch (Exception e) {
            return Map.of("role", "tool", "content", content, "tool_call_id", "legacy");
        }
    }

    /**
     * Compacts the full chat history for a user into a single SYSTEM summary message.
     * The old messages are deleted and replaced with the LLM-generated summary.
     */
    @Transactional
    public String compactHistory(Long userId) {
        List<ChatMessage> history = chatMessageRepository.findRecentByUserId(userId, Integer.MAX_VALUE);
        if (history.isEmpty()) {
            log.debug("No chat history to compact for userId={}", userId);
            return "Nothing to compact.";
        }

        String rawHistory = history.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        log.info("Compacting {} messages for userId={}", history.size(), userId);

        String summary = llmService.chat(COMPACTION_PROMPT, rawHistory);

        chatMessageRepository.deleteAllByUserId(userId);
        chatMessageRepository.saveCompactedSummary(userId, summary);

        log.info("Context compacted successfully for userId={}", userId);
        return "Context compacted successfully.";
    }
}
