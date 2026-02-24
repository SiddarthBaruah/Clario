package com.assistant.core.service;

import com.assistant.core.dto.AssistantProfileResponseDTO;
import com.assistant.core.dto.UpdatePersonalityRequestDTO;
import com.assistant.core.model.AssistantProfile;
import com.assistant.core.repository.AssistantProfileRepository;
import com.assistant.core.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantProfileService {

    private static final Logger log = LoggerFactory.getLogger(AssistantProfileService.class);

    public static final String DEFAULT_ASSISTANT_NAME = "Astra";
    public static final String DEFAULT_PERSONALITY = """
            You are a highly intelligent, organized and emotionally aware personal assistant.
            You help the user remember tasks, people, and commitments clearly and concisely.""";

    private final AssistantProfileRepository assistantProfileRepository;

    public AssistantProfileService(AssistantProfileRepository assistantProfileRepository) {
        this.assistantProfileRepository = assistantProfileRepository;
    }

    /**
     * Creates a default assistant profile for the user if they do not have one yet.
     * Call after first login (or register).
     */
    @Transactional
    public AssistantProfileResponseDTO createDefaultProfileOnFirstLogin(Long userId) {
        var existing = assistantProfileRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return toResponseDTO(existing.get());
        }
        AssistantProfile profile = new AssistantProfile();
        profile.setUserId(userId);
        profile.setAssistantName(DEFAULT_ASSISTANT_NAME);
        profile.setPersonalityPrompt(DEFAULT_PERSONALITY);
        profile = assistantProfileRepository.save(profile);
        log.info("Default assistant profile created for userId={}", userId);
        return toResponseDTO(profile);
    }

    /**
     * Returns the profile for the given user. Use for MCP system context.
     */
    public AssistantProfileResponseDTO getProfile(Long userId) {
        AssistantProfile profile = assistantProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Assistant profile not found for user"));
        return toResponseDTO(profile);
    }

    /**
     * Returns the personality prompt for the given user, for use as LLM system context.
     * Returns default prompt if no profile exists (e.g. before first login).
     */
    public String getSystemContextPrompt(Long userId) {
        return assistantProfileRepository.findByUserId(userId)
                .map(AssistantProfile::getPersonalityPrompt)
                .filter(p -> p != null && !p.isBlank())
                .orElse(DEFAULT_PERSONALITY);
    }

    @Transactional
    public AssistantProfileResponseDTO updatePersonality(Long userId, UpdatePersonalityRequestDTO request) {
        AssistantProfile profile = assistantProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Assistant profile not found for user"));
        String prompt = InputSanitizer.sanitizeLongText(request.getPersonalityPrompt());
        profile.setPersonalityPrompt(prompt != null ? prompt : "");
        profile = assistantProfileRepository.save(profile);
        log.info("Assistant profile updated for userId={}", userId);
        return toResponseDTO(profile);
    }

    private AssistantProfileResponseDTO toResponseDTO(AssistantProfile p) {
        AssistantProfileResponseDTO dto = new AssistantProfileResponseDTO();
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setAssistantName(p.getAssistantName());
        dto.setPersonalityPrompt(p.getPersonalityPrompt());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
