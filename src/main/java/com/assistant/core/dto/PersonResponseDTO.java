package com.assistant.core.dto;

import java.time.Instant;

public class PersonResponseDTO {

    private Long id;
    private String name;
    private String notes;
    private String importantDates;
    private Instant createdAt;

    public PersonResponseDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getImportantDates() { return importantDates; }
    public void setImportantDates(String importantDates) { this.importantDates = importantDates; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
