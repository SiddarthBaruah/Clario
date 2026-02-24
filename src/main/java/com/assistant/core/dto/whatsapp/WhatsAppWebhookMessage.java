package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookMessage {

    private String from;  // WhatsApp ID (phone number, e.g. 919876543210)
    private String id;
    private String timestamp;
    private String type;  // text, image, etc.
    private WhatsAppWebhookText text;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public WhatsAppWebhookText getText() { return text; }
    public void setText(WhatsAppWebhookText text) { this.text = text; }
}
