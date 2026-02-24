package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookValue {

    @JsonProperty("messaging_product")
    private String messagingProduct;
    private List<WhatsAppWebhookMessage> messages;

    public String getMessagingProduct() { return messagingProduct; }
    public void setMessagingProduct(String messagingProduct) { this.messagingProduct = messagingProduct; }
    public List<WhatsAppWebhookMessage> getMessages() { return messages; }
    public void setMessages(List<WhatsAppWebhookMessage> messages) { this.messages = messages; }
}
