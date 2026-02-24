package com.assistant.core.service;

import com.assistant.core.mcp.LLMService;
import com.assistant.core.model.ChatMessage;
import com.assistant.core.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);
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
        return chatMessageRepository.save(msg);
    }

    public ChatMessage saveAssistantMessage(Long userId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        return chatMessageRepository.save(msg);
    }

    /**
     * Returns the recent conversation history for a user as a single formatted string
     * suitable for injection into an LLM prompt.
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
