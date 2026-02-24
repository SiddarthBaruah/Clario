package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookText {

    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
