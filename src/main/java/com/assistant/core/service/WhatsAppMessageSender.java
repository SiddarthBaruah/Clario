package com.assistant.core.service;

/**
 * Abstraction for sending a WhatsApp message. Implementations can log to console,
 * call WhatsApp Business Cloud API via RestClient, or use a queue.
 */
public interface WhatsAppMessageSender {

    /**
     * Send a text message to the given phone number (E.164 or WhatsApp ID).
     */
    void send(String phoneNumber, String message);
}
