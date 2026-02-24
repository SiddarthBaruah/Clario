package com.assistant.core.model;

import java.time.Instant;

public class People {

    private Long id;
    private Long userId;
    private String name;
    private String notes;
    private String importantDates; // JSON text
    private Instant createdAt;

    public People() {
    }

    public People(Long id, Long userId, String name, String notes, String importantDates, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.notes = notes;
        this.importantDates = importantDates;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getImportantDates() { return importantDates; }
    public void setImportantDates(String importantDates) { this.importantDates = importantDates; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
