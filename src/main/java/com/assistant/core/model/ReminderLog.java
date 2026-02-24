package com.assistant.core.model;

import java.time.Instant;

public class ReminderLog {

    private Long id;
    private Long taskId;
    private Instant sentAt;
    private String status;

    public ReminderLog() {}

    public ReminderLog(Long id, Long taskId, Instant sentAt, String status) {
        this.id = id;
        this.taskId = taskId;
        this.sentAt = sentAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
