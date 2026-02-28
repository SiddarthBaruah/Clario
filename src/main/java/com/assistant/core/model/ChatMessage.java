package com.assistant.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    /** Visibility: INTERNAL (AI-only, tool calls/results) or USER_FACING (shown in chat UI). */
    public static final String VISIBILITY_INTERNAL = "INTERNAL";
    public static final String VISIBILITY_USER_FACING = "USER_FACING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;   // USER, ASSISTANT, SYSTEM, TOOL

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "visibility", length = 20)
    private String visibility;

    @Column(name = "created_at")
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
