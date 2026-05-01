package com.therapea.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Patient Information
    private String email;
    private String userId;
    private String patientName;

    // NEW: Doctor/Provider Information (Private Inbox Linking)
    private String providerEmail;
    private String providerName;

    private String assessmentType;
    private Integer phq9Score;
    private Integer gad7Score;
    private Integer totalScore;
    private Integer clinicalScore;
    private String riskLevel;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String answers;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}