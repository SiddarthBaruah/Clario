package com.assistant.core.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Meta WhatsApp webhook POST body structure.
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/payload-examples">Payload Examples</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookPayload {

    private String object;
    private List<WhatsAppWebhookEntry> entry;

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public List<WhatsAppWebhookEntry> getEntry() { return entry; }
    public void setEntry(List<WhatsAppWebhookEntry> entry) { this.entry = entry; }
}
