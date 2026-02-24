package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookChange {

    private String field;
    private WhatsAppWebhookValue value;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public WhatsAppWebhookValue getValue() { return value; }
    public void setValue(WhatsAppWebhookValue value) { this.value = value; }
}
