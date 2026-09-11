package com.interviewbooth.dto;

import jakarta.validation.constraints.NotBlank;

public class StartSessionRequest {
    @NotBlank
    private String role;       // e.g. "Java Developer"
    @NotBlank
    private String difficulty; // Beginner / Intermediate / Advanced
    @NotBlank
    private String roundType;  // Technical / Resume-Based / HR Round

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getRoundType() { return roundType; }
    public void setRoundType(String roundType) { this.roundType = roundType; }
}
