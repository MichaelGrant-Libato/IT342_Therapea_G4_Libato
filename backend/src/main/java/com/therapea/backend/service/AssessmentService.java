package com.therapea.backend.service;

import com.therapea.backend.dto.AssessmentRequest;
import com.therapea.backend.entity.Assessment;
import com.therapea.backend.repository.AssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssessmentService {

    @Autowired
    private AssessmentRepository assessmentRepository;

    public Assessment createAssessment(AssessmentRequest request) {
        Assessment assessment = Assessment.builder()
                .email(request.getEmail())
                .assessmentType(request.getAssessmentType())
                .clinicalScore(request.getClinicalScore())
                .riskLevel(request.getRiskLevel())
                .status(request.getStatus())
                .build();

        return assessmentRepository.save(assessment);
    }

    public List<Assessment> getUserAssessments(String email) {
        return assessmentRepository.findByEmailOrderByCreatedAtDesc(email);
    }

    public void markAsReviewed(Long id) {
        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        assessment.setStatus("Reviewed");
        assessmentRepository.save(assessment);
    }
}