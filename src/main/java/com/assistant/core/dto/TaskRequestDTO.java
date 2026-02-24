package com.assistant.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class TaskRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 500)
    private String title;

    private String description;
    private Instant dueTime;
    private Instant reminderTime;

    public TaskRequestDTO() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getDueTime() { return dueTime; }
    public void setDueTime(Instant dueTime) { this.dueTime = dueTime; }
    public Instant getReminderTime() { return reminderTime; }
    public void setReminderTime(Instant reminderTime) { this.reminderTime = reminderTime; }
}
