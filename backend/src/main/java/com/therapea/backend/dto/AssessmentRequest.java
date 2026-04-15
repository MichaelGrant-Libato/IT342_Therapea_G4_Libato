package com.therapea.backend.dto;

import lombok.Data;

@Data
public class AssessmentRequest {
    private String email;
    private String assessmentType;
    private Integer clinicalScore;
    private String riskLevel;
    private String status;
}