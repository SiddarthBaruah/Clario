package com.assistant.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddPersonRequestDTO {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 10000)
    private String notes;

    @Size(max = 5000)
    private String importantDates; // JSON string

    public AddPersonRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getImportantDates() { return importantDates; }
    public void setImportantDates(String importantDates) { this.importantDates = importantDates; }
}
