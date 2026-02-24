package com.assistant.core.dto;

import jakarta.validation.constraints.Size;

public class UpdatePersonNotesRequestDTO {

    @Size(max = 10000, message = "Notes must not exceed 10000 characters")
    private String notes;

    public UpdatePersonNotesRequestDTO() {}

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
