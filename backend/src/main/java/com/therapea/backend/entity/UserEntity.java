package com.therapea.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column
    private Boolean emailVerified = false;

    @Column
    private Boolean isActive = true;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLogin;

    @Column
    private Integer sessionHours = 24;

    @Column
    private Boolean alwaysLoggedIn = false;

    @Column
    private LocalDateTime verifiedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private Boolean profileCompleted = false;

    @Column(columnDefinition = "TEXT")
    private String clinicalBio;

    @Column
    private Double hourlyRate;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "status")
    private String status = "APPROVED";

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "rejection_reason")
    private String rejectionReason;


    @Column(name = "prc_license_data")
    private byte[] prcLicenseData;

    @Column(name = "prc_license_type")
    private String prcLicenseType;

    // ==========================================
    // Getters and Setters
    // ==========================================
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public Integer getSessionHours() { return sessionHours; }
    public void setSessionHours(Integer sessionHours) { this.sessionHours = sessionHours; }

    public Boolean getAlwaysLoggedIn() { return alwaysLoggedIn; }
    public void setAlwaysLoggedIn(Boolean alwaysLoggedIn) { this.alwaysLoggedIn = alwaysLoggedIn; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Boolean getProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(Boolean profileCompleted) { this.profileCompleted = profileCompleted; }

    public String getClinicalBio() { return clinicalBio; }
    public void setClinicalBio(String clinicalBio) { this.clinicalBio = clinicalBio; }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public byte[] getPrcLicenseData() { return prcLicenseData; }
    public void setPrcLicenseData(byte[] prcLicenseData) { this.prcLicenseData = prcLicenseData; }

    public String getPrcLicenseType() { return prcLicenseType; }
    public void setPrcLicenseType(String prcLicenseType) { this.prcLicenseType = prcLicenseType; }
}