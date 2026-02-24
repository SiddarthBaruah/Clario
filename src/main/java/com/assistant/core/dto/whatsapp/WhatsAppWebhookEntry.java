package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookEntry {

    private String id;
    private List<WhatsAppWebhookChange> changes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<WhatsAppWebhookChange> getChanges() { return changes; }
    public void setChanges(List<WhatsAppWebhookChange> changes) { this.changes = changes; }
}
