package com.assistant.core.dto;

public class LoginResponseDTO {

    private String token;
    private String email;
    private long expiresInSeconds;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, String email, long expiresInSeconds) {
        this.token = token;
        this.email = email;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
