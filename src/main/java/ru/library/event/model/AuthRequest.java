package ru.library.event.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthRequest {
    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String userId;

    @NotBlank
    @Size(min = 6, max = 72)
    private String password;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
