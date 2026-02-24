package com.assistant.core.controller;

import com.assistant.core.dto.ApiResponse;
import com.assistant.core.dto.AssistantProfileResponseDTO;
import com.assistant.core.dto.UpdatePersonalityRequestDTO;
import com.assistant.core.repository.UserRepository;
import com.assistant.core.service.AssistantProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final AssistantProfileService assistantProfileService;
    private final UserRepository userRepository;

    public AssistantController(AssistantProfileService assistantProfileService,
                                      UserRepository userRepository) {
        this.assistantProfileService = assistantProfileService;
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<AssistantProfileResponseDTO>> getProfile(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        AssistantProfileResponseDTO profile = assistantProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<AssistantProfileResponseDTO>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdatePersonalityRequestDTO request) {
        Long userId = resolveUserId(authentication);
        AssistantProfileResponseDTO profile = assistantProfileService.updatePersonality(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    private Long resolveUserId(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        if (email == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
