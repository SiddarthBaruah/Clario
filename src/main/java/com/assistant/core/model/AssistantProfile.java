package com.assistant.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "assistant_profile")
public class AssistantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "assistant_name", nullable = false)
    private String assistantName;

    @Column(name = "personality_prompt", columnDefinition = "TEXT")
    private String personalityPrompt;

    @Column(name = "created_at")
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
