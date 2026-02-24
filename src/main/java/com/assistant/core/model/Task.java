package com.assistant.core.model;

import java.time.Instant;

public class Task {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Instant dueTime;
    private Instant reminderTime;
    private String status; // PENDING, DONE
    private Instant createdAt;

    public Task() {
    }

    public Task(Long id, Long userId, String title, String description, Instant dueTime,
               Instant reminderTime, String status, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.dueTime = dueTime;
        this.reminderTime = reminderTime;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getDueTime() { return dueTime; }
    public void setDueTime(Instant dueTime) { this.dueTime = dueTime; }
    public Instant getReminderTime() { return reminderTime; }
    public void setReminderTime(Instant reminderTime) { this.reminderTime = reminderTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
