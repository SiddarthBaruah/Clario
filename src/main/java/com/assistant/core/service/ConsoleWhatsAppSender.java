package com.assistant.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Logs outgoing messages to the console. Enable with app.whatsapp.outbound=console.
 * Default is bridge (BridgeWhatsAppSender).
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.outbound", havingValue = "console")
public class ConsoleWhatsAppSender implements WhatsAppMessageSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWhatsAppSender.class);

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[WhatsApp outbound] To {}: {}", maskPhone(phoneNumber), message);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }
}
