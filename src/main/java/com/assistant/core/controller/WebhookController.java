package com.assistant.core.controller;

import com.assistant.core.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @PostMapping("/{type}")
    public ResponseEntity<ApiResponse<Map<String, String>>> handleWebhook(
            @PathVariable String type,
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        log.debug("Webhook received: type={}, signature present={}", type, signature != null);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("received", "true", "type", type)));
    }
}
