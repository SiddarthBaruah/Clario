package com.assistant.core.controller;

import com.assistant.core.dto.ApiResponse;
import com.assistant.core.dto.whatsapp.WhatsAppWebhookPayload;
import com.assistant.core.service.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta WhatsApp webhook: verification (GET) and incoming messages (POST).
 * Outbound WhatsApp API send is not implemented; response text is only formulated.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    @Value("${app.whatsapp.verify-token:}")
    private String verifyToken;

    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(WhatsAppWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * GET /webhook/whatsapp — Meta Webhook Verification.
     * Accepts hub.mode, hub.challenge, hub.verify_token. Returns hub.challenge if token matches.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.challenge") String challenge,
            @RequestParam(name = "hub.verify_token") String token) {
        if (verifyToken == null || verifyToken.isBlank()) {
            log.warn("WhatsApp verify token not configured");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verify token not configured");
        }
        if (!verifyToken.equals(token)) {
            log.warn("WhatsApp verify token mismatch");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Token mismatch");
        }
        log.debug("WhatsApp webhook verified, mode={}", mode);
        return ResponseEntity.ok(challenge);
    }

    /**
     * POST /webhook/whatsapp — Incoming message forwarded by the Node.js bridge.
     * Processes the message and returns the reply text. The bridge reads the response and sends
     * it back to the user via Baileys. Always returns 200 so the bridge can extract the reply.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WhatsAppWebhookResponse>> handleIncoming(@RequestBody(required = false) WhatsAppWebhookPayload payload) {
        if (payload == null || !"whatsapp_business_account".equals(payload.getObject())) {
            log.warn("Webhook receipt: invalid or missing payload (object={})", payload != null ? payload.getObject() : "null");
            return ResponseEntity.ok(ApiResponse.ok(
                    new WhatsAppWebhookResponse("Sorry, I couldn't understand that message.")));
        }
        log.info("Webhook receipt: incoming WhatsApp message");
        String responseText = webhookService.processIncomingMessage(payload);
        return ResponseEntity.ok(ApiResponse.ok(new WhatsAppWebhookResponse(responseText)));
    }

    /**
     * Formulated response message (to be sent via WhatsApp API later).
     */
    public static class WhatsAppWebhookResponse {
        private final String responseMessage;

        public WhatsAppWebhookResponse(String responseMessage) {
            this.responseMessage = responseMessage;
        }

        public String getResponseMessage() {
            return responseMessage;
        }
    }
}
