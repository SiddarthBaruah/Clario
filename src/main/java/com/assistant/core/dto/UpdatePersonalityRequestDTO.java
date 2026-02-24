package com.assistant.core.dto;

import jakarta.validation.constraints.Size;

public class UpdatePersonalityRequestDTO {

    @Size(max = 10000, message = "Personality prompt must not exceed 10000 characters")
    private String personalityPrompt;

    public UpdatePersonalityRequestDTO() {}

    public String getPersonalityPrompt() { return personalityPrompt; }
    public void setPersonalityPrompt(String personalityPrompt) { this.personalityPrompt = personalityPrompt; }
}
