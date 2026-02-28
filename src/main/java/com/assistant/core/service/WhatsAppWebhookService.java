package com.assistant.core.service;

import com.assistant.core.dto.whatsapp.WhatsAppWebhookMessage;
import com.assistant.core.dto.whatsapp.WhatsAppWebhookPayload;
import com.assistant.core.model.User;
import com.assistant.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Handles WhatsApp webhook POST: map phone to user, delegate to conversation loop, return response text.
 * No outbound WhatsApp API call—only formulates the response message.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    private static final String COMPACT_COMMAND = "/compact";

    private final UserRepository userRepository;
    private final ChatMemoryService chatMemoryService;
    private final ConversationOrchestratorService conversationOrchestrator;

    public WhatsAppWebhookService(UserRepository userRepository,
                                  ChatMemoryService chatMemoryService,
                                  ConversationOrchestratorService conversationOrchestrator) {
        this.userRepository = userRepository;
        this.chatMemoryService = chatMemoryService;
        this.conversationOrchestrator = conversationOrchestrator;
    }

    /**
     * Validates payload, extracts phone and message, maps to user, runs conversation loop, returns response text.
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
            String reply = conversationOrchestrator.processMessage(userId, messageText);
            log.info("Webhook reply generated for userId={}", userId);
            return reply;
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
}
