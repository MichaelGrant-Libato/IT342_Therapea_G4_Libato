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
        // Using standard object instantiation instead of .builder() to fix the compilation error
        Assessment assessment = new Assessment();

        assessment.setEmail(request.getEmail());
        assessment.setUserId(request.getUserId());
        assessment.setAssessmentType(request.getAssessmentType());
        assessment.setPhq9Score(request.getPhq9Score());
        assessment.setGad7Score(request.getGad7Score());
        assessment.setTotalScore(request.getTotalScore());
        assessment.setClinicalScore(request.getClinicalScore());
        assessment.setRiskLevel(request.getRiskLevel());
        assessment.setStatus(request.getStatus());
        assessment.setAnswers(request.getAnswers());

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