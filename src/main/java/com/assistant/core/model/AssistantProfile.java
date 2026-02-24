package com.assistant.core.model;

import java.time.Instant;

public class AssistantProfile {

    private Long id;
    private Long userId;
    private String assistantName;
    private String personalityPrompt;
    private Instant createdAt;

    public AssistantProfile() {
    }

    public AssistantProfile(Long id, Long userId, String assistantName, String personalityPrompt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.assistantName = assistantName;
        this.personalityPrompt = personalityPrompt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAssistantName() { return assistantName; }
    public void setAssistantName(String assistantName) { this.assistantName = assistantName; }
    public String getPersonalityPrompt() { return personalityPrompt; }
    public void setPersonalityPrompt(String personalityPrompt) { this.personalityPrompt = personalityPrompt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
