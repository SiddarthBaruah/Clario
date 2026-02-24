package com.assistant.core.service;

import com.assistant.core.dto.LoginRequestDTO;
import com.assistant.core.dto.LoginResponseDTO;
import com.assistant.core.dto.RegisterRequestDTO;
import com.assistant.core.dto.UserResponseDTO;
import com.assistant.core.model.User;
import com.assistant.core.repository.UserRepository;
import com.assistant.core.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AssistantProfileService assistantProfileService;

    @Value("${app.security.jwt.expiration-seconds}")
    private long expirationSeconds;

    public UserService(UserRepository userRepository, JwtService jwtService,
                       PasswordEncoder passwordEncoder, AssistantProfileService assistantProfileService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.assistantProfileService = assistantProfileService;
    }

    @Transactional
    public LoginResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new IllegalArgumentException("Phone number already registered");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setUsername(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);
        assistantProfileService.createDefaultProfileOnFirstLogin(user.getId());
        String token = jwtService.createToken(user.getEmail());
        log.info("User registered: userId={}, email={}", user.getId(), user.getEmail());
        return new LoginResponseDTO(token, user.getEmail(), expirationSeconds);
    }

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        assistantProfileService.createDefaultProfileOnFirstLogin(user.getId());
        String token = jwtService.createToken(user.getEmail());
        log.info("User login: userId={}, email={}", user.getId(), user.getEmail());
        return new LoginResponseDTO(token, user.getEmail(), expirationSeconds);
    }

    public UserResponseDTO findByPhone(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found for phone number"));
        return toUserResponseDTO(user);
    }

    private UserResponseDTO toUserResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        return dto;
    }
}
