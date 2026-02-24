package com.assistant.core.service;

import com.assistant.core.dto.whatsapp.WhatsAppWebhookMessage;
import com.assistant.core.dto.whatsapp.WhatsAppWebhookPayload;
import com.assistant.core.model.User;
import com.assistant.core.mcp.LLMService;
import com.assistant.core.mcp.ToolCallResponse;
import com.assistant.core.mcp.ToolRouter;
import com.assistant.core.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles WhatsApp webhook POST: map phone to user, call LLM, route tool, format response text.
 * No outbound WhatsApp API call—only formulates the response message.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String COMPACT_COMMAND = "/compact";

    private final UserRepository userRepository;
    private final LLMService llmService;
    private final ToolRouter toolRouter;
    private final ChatMemoryService chatMemoryService;

    public WhatsAppWebhookService(UserRepository userRepository, LLMService llmService,
                                  ToolRouter toolRouter, ChatMemoryService chatMemoryService) {
        this.userRepository = userRepository;
        this.llmService = llmService;
        this.toolRouter = toolRouter;
        this.chatMemoryService = chatMemoryService;
    }

    /**
     * Validates payload, extracts phone and message, maps to user, runs LLM + tool, returns response text.
     * Never throws for user-facing issues — always returns a reply string so the bridge can send it back.
     */
    public String processIncomingMessage(WhatsAppWebhookPayload payload) {
        String phoneNumber = extractPhoneNumber(payload);
        String messageText = extractMessageText(payload);

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("Could not extract phone number from webhook payload");
            return "Sorry, I couldn't identify your phone number. Please try again.";
        }
        if (messageText == null || messageText.isBlank()) {
            log.debug("Non-text message received from {}", phoneNumber);
            return "Sorry, I can only process text messages at the moment.";
        }

        log.info("Webhook processing: from={}, messageLength={}", phoneNumber, messageText.length());

        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber)
                .or(() -> tryFindByNormalizedPhone(phoneNumber));
        if (userOpt.isEmpty()) {
            log.warn("No user found for phone number: {}", phoneNumber);
            return "Sorry, your phone number (" + phoneNumber + ") is not registered with Clario. "
                    + "Please sign up first or contact support.";
        }
        User user = userOpt.get();
        Long userId = user.getId();

        if (COMPACT_COMMAND.equalsIgnoreCase(messageText.strip())) {
            log.info("Compaction requested by userId={}", userId);
            return chatMemoryService.compactHistory(userId);
        }

        try {
            chatMemoryService.saveUserMessage(userId, messageText);
            String history = chatMemoryService.getConversationHistory(userId);

            ToolCallResponse toolCall = llmService.requestToolCall(userId, messageText, history);
            log.info("Webhook tool call: userId={}, tool={}", userId, toolCall.tool());
            Map<String, Object> toolResult = toolRouter.invoke(toolCall.tool(), ensureUserId(toolCall.parameters(), userId));

            String naturalResponse = llmService.generateNaturalResponse(
                    userId, messageText, toolCall.tool(), toolResult, history);
            log.info("Webhook natural response generated for userId={}", userId);

            chatMemoryService.saveAssistantMessage(userId, naturalResponse);
            return naturalResponse;
        } catch (Exception e) {
            log.error("Error processing message for userId={}: {}", userId, e.getMessage(), e);
            return "Sorry, something went wrong while processing your request. Please try again later.";
        }
    }

    private String extractPhoneNumber(WhatsAppWebhookPayload payload) {
        if (payload == null || payload.getEntry() == null || payload.getEntry().isEmpty()) return null;
        var change = payload.getEntry().get(0).getChanges();
        if (change == null || change.isEmpty() || change.get(0).getValue() == null) return null;
        var messages = change.get(0).getValue().getMessages();
        if (messages == null || messages.isEmpty()) return null;
        return messages.get(0).getFrom();
    }

    private String extractMessageText(WhatsAppWebhookPayload payload) {
        if (payload == null || payload.getEntry() == null || payload.getEntry().isEmpty()) return null;
        var change = payload.getEntry().get(0).getChanges();
        if (change == null || change.isEmpty() || change.get(0).getValue() == null) return null;
        var messages = change.get(0).getValue().getMessages();
        if (messages == null || messages.isEmpty()) return null;
        WhatsAppWebhookMessage msg = messages.get(0);
        if (msg.getText() == null) return null;
        return msg.getText().getBody();
    }

    private Optional<User> tryFindByNormalizedPhone(String whatsappFrom) {
        if (whatsappFrom == null || whatsappFrom.isBlank()) {
            return Optional.empty();
        }

        // Remove all non-digit characters
        String digits = whatsappFrom.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }

        // 1. Try with + prefix (expected DB format like +9188...)
        Optional<User> user = userRepository.findByPhoneNumber("+" + digits);
        if (user.isPresent()) {
            return user;
        }

        // 2. Try exact digits match (if DB stored without +)
        user = userRepository.findByPhoneNumber(digits);
        if (user.isPresent()) {
            return user;
        }

        // 3. If longer than 10 digits, try last 10 digits (India/local fallback)
        if (digits.length() > 10) {
            String lastTen = digits.substring(digits.length() - 10);

            user = userRepository.findByPhoneNumber("+" + lastTen);
            if (user.isPresent()) {
                return user;
            }

            return userRepository.findByPhoneNumber(lastTen);
        }

        return Optional.empty();
    }

    private Map<String, Object> ensureUserId(Map<String, Object> parameters, Long userId) {
        Map<String, Object> params = new LinkedHashMap<>(parameters != null ? parameters : Map.of());
        params.put("userId", userId);
        return params;
    }

    private String formatResponseMessage(String toolName, Map<String, Object> toolResult) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(toolName).append("\n");
            if (toolResult.containsKey("tasks")) {
                @SuppressWarnings("unchecked")
                var tasks = (java.util.List<Map<String, Object>>) toolResult.get("tasks");
                sb.append("Pending tasks: ").append(toolResult.get("count")).append("\n");
                if (tasks != null && !tasks.isEmpty()) {
                    for (int i = 0; i < tasks.size(); i++) {
                        Map<String, Object> t = tasks.get(i);
                        sb.append(i + 1).append(". ").append(t.get("title"));
                        if (t.get("dueTime") != null) sb.append(" (due: ").append(t.get("dueTime")).append(")");
                        sb.append("\n");
                    }
                }
            } else if (toolResult.containsKey("people")) {
                @SuppressWarnings("unchecked")
                var people = (java.util.List<Map<String, Object>>) toolResult.get("people");
                sb.append("Contacts: ").append(toolResult.get("count")).append("\n");
                if (people != null && !people.isEmpty()) {
                    for (int i = 0; i < people.size(); i++) {
                        Map<String, Object> p = people.get(i);
                        sb.append(i + 1).append(". ").append(p.get("name"));
                        if (p.get("notes") != null && !p.get("notes").toString().isBlank())
                            sb.append(" - ").append(p.get("notes"));
                        sb.append("\n");
                    }
                }
            } else if (toolResult.containsKey("id")) {
                sb.append("Done. ");
                if (toolResult.get("title") != null) sb.append("Task: ").append(toolResult.get("title"));
                else if (toolResult.get("name") != null) sb.append("Added: ").append(toolResult.get("name"));
                sb.append("\n");
            } else {
                sb.append(OBJECT_MAPPER.writeValueAsString(toolResult));
            }
            return sb.toString().trim();
        } catch (JsonProcessingException e) {
            return "Action completed. " + toolResult;
        }
    }
}
