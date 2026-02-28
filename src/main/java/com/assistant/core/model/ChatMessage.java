package com.assistant.core.model;

import java.time.Instant;

public class ChatMessage {

    /** Visibility: INTERNAL (AI-only, tool calls/results) or USER_FACING (shown in chat UI). */
    public static final String VISIBILITY_INTERNAL = "INTERNAL";
    public static final String VISIBILITY_USER_FACING = "USER_FACING";

    private Long id;
    private Long userId;
    private String role;   // USER, ASSISTANT, SYSTEM, TOOL
    private String content;
    private String visibility;
    private Instant createdAt;

    public ChatMessage() {
    }

    public ChatMessage(Long id, Long userId, String role, String content, String visibility, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.visibility = visibility;
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
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
