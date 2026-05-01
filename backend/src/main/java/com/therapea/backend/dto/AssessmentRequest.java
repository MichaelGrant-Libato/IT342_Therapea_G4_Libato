package com.therapea.backend.dto;

public class AssessmentRequest {
    private String email;
    private String userId;
    private String assessmentType;
    private Integer phq9Score;
    private Integer gad7Score;
    private Integer totalScore;
    private Integer clinicalScore;
    private String riskLevel;
    private String status;
    private String answers;

    // Getters
    public String getEmail() { return email; }
    public String getUserId() { return userId; }
    public String getAssessmentType() { return assessmentType; }
    public Integer getPhq9Score() { return phq9Score; }
    public Integer getGad7Score() { return gad7Score; }
    public Integer getTotalScore() { return totalScore; }
    public Integer getClinicalScore() { return clinicalScore; }
    public String getRiskLevel() { return riskLevel; }
    public String getStatus() { return status; }
    public String getAnswers() { return answers; }

    // Setters
    public void setEmail(String email) { this.email = email; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    public void setPhq9Score(Integer phq9Score) { this.phq9Score = phq9Score; }
    public void setGad7Score(Integer gad7Score) { this.gad7Score = gad7Score; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public void setClinicalScore(Integer clinicalScore) { this.clinicalScore = clinicalScore; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setStatus(String status) { this.status = status; }
    public void setAnswers(String answers) { this.answers = answers; }
}