package com.therapea.backend.controller;

import com.therapea.backend.dto.AssessmentRequest;
import com.therapea.backend.entity.Assessment;
import com.therapea.backend.service.AssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/assessments")
@CrossOrigin(origins = "http://localhost:5173") // Allow React to talk to Spring Boot
public class AssessmentController {

    @Autowired
    private AssessmentService assessmentService;

    // 1. Submit a new assessment
    @PostMapping
    public ResponseEntity<?> submitAssessment(@RequestBody AssessmentRequest request) {
        try {
            Assessment saved = assessmentService.createAssessment(request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Assessment saved successfully");
            response.put("data", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // 2. Fetch assessments for the Dashboard
    @GetMapping
    public ResponseEntity<?> getAssessments(@RequestParam String email) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("assessments", assessmentService.getUserAssessments(email));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // 3. Doctor action to mark assessment as reviewed
    @PatchMapping("/{id}/review")
    public ResponseEntity<?> reviewAssessment(@PathVariable Long id) {
        try {
            assessmentService.markAsReviewed(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Marked as reviewed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}