package com.assistant.core.controller;

import com.assistant.core.dto.AddPersonRequestDTO;
import com.assistant.core.dto.ApiResponse;
import com.assistant.core.dto.PageResponseDTO;
import com.assistant.core.dto.PersonResponseDTO;
import com.assistant.core.repository.UserRepository;
import com.assistant.core.service.PeopleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/people")
public class PeopleController {

    private final PeopleService peopleService;
    private final UserRepository userRepository;

    @Value("${app.pagination.default-size:20}")
    private int defaultPageSize;
    @Value("${app.pagination.max-size:100}")
    private int maxPageSize;

    public PeopleController(PeopleService peopleService, UserRepository userRepository) {
        this.peopleService = peopleService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<PersonResponseDTO>>> getPeople(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(authentication);
        if (page < 0) page = 0;
        if (size <= 0) size = defaultPageSize;
        if (size > maxPageSize) size = maxPageSize;
        PageResponseDTO<PersonResponseDTO> result = peopleService.listPeople(userId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonResponseDTO>> addPerson(
            Authentication authentication,
            @Valid @RequestBody AddPersonRequestDTO request) {
        Long userId = resolveUserId(authentication);
        PersonResponseDTO person = peopleService.addPerson(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(person));
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
