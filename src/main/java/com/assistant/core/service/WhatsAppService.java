package com.assistant.core.service;

import org.springframework.stereotype.Service;

/**
 * Facade for sending WhatsApp messages. Delegates to a {@link WhatsAppMessageSender}
 * (default: {@link BridgeWhatsAppSender} POSTs to the Node.js bridge at app.whatsapp.bridge-url/send).
 */
@Service
public class WhatsAppService {

    private final WhatsAppMessageSender sender;

    public WhatsAppService(WhatsAppMessageSender sender) {
        this.sender = sender;
    }

    /**
     * Send a text message to the given phone number via the configured sender.
     * With default bridge sender: POSTs to the Node.js bridge; phone number is formatted with @s.whatsapp.net if needed.
     */
    public void sendMessage(String phoneNumber, String message) {
        sender.send(phoneNumber, message);
    }

    /**
     * Convenience for reminders; delegates to {@link #sendMessage(String, String)}.
     */
    public void sendReminder(String phoneNumber, String message) {
        sendMessage(phoneNumber, message);
    }
}
