package com.assistant.core.model;

import java.time.Instant;

public class ChatMessage {

    private Long id;
    private Long userId;
    private String role;   // USER, ASSISTANT, SYSTEM
    private String content;
    private Instant createdAt;

    public ChatMessage() {
    }

    public ChatMessage(Long id, Long userId, String role, String content, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
