package com.assistant.core.controller;

import com.assistant.core.dto.ApiResponse;
import com.assistant.core.dto.LoginRequestDTO;
import com.assistant.core.dto.LoginResponseDTO;
import com.assistant.core.dto.RegisterRequestDTO;
import com.assistant.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> register(@Valid @RequestBody RegisterRequestDTO request) {
        LoginResponseDTO response = userService.register(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
