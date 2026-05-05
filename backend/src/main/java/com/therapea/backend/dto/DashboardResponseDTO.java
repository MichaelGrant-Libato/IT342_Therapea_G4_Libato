package com.therapea.backend.dto;

import com.therapea.backend.features.auth.SessionDTO;

import java.util.List;

public class DashboardResponseDTO {
    public boolean success;
    public String userId;
    public String email;
    public String fullName;
    public String role;
    public boolean emailVerified;
    public boolean profileCompleted;
    public String createdAt;
    public String lastLogin;

    // 🔴 Changed from private to public!
    public String profilePictureUrl;

    // Dynamic Data
    public List<StatDTO> stats;
    public List<SessionDTO> sessions;
    public List<ProgressDTO> progressItems;
}