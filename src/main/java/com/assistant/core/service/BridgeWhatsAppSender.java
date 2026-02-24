package com.assistant.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends WhatsApp messages via the Node.js Baileys bridge (POST to /send).
 * Default outbound implementation when app.whatsapp.outbound is unset or "bridge".
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.outbound", havingValue = "bridge", matchIfMissing = true)
public class BridgeWhatsAppSender implements WhatsAppMessageSender {

    private static final Logger log = LoggerFactory.getLogger(BridgeWhatsAppSender.class);
    private static final String JID_SUFFIX = "@s.whatsapp.net";

    private final RestClient restClient;

    public BridgeWhatsAppSender(
            @Value("${app.whatsapp.bridge-url:http://localhost:3000}") String bridgeBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(bridgeBaseUrl != null ? bridgeBaseUrl.strip() : "http://localhost:3000")
                .build();
    }

    @Override
    public void send(String phoneNumber, String message) {
        String to = formatJid(phoneNumber);
        var body = new SendBody(to, message);
        try {
            restClient.post()
                    .uri("/send")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Sent WhatsApp message to {}", to);
        } catch (Exception e) {
            log.error("Bridge send failed for {}: {}", to, e.getMessage());
            throw new RuntimeException("WhatsApp bridge send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures the destination has the @s.whatsapp.net suffix for the bridge.
     */
    private static String formatJid(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber must not be blank");
        }
        String normalized = phoneNumber.strip();
        if (normalized.endsWith(JID_SUFFIX)) {
            return normalized;
        }
        return normalized + JID_SUFFIX;
    }

    private record SendBody(String to, String text) {}
}
