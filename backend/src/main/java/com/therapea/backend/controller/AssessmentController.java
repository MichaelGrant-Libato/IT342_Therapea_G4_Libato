package com.therapea.backend.controller;

import com.therapea.backend.entity.Assessment;
import com.therapea.backend.repository.AssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assessments")
@CrossOrigin(origins = "*") // Adjust to your frontend port
public class AssessmentController {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveAssessment(@RequestBody Assessment assessment) {
        try {
            Assessment saved = assessmentRepository.save(assessment);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("assessment", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ─── PATIENT INBOX: Gets only their own past assessments ───
    @GetMapping("/user")
    public ResponseEntity<?> getUserAssessments(@RequestParam String email) {
        try {
            List<Assessment> assessments = assessmentRepository.findByEmailOrderByCreatedAtDesc(email);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("assessments", assessments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ─── DOCTOR INBOX: Gets ONLY assessments assigned to them ───
    @GetMapping("/doctor-queue")
    public ResponseEntity<?> getDoctorAssessments(@RequestParam String doctorEmail) {
        try {
            List<Assessment> doctorAssessments = assessmentRepository.findByProviderEmailOrderByCreatedAtDesc(doctorEmail);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("assessments", doctorAssessments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAssessment(@PathVariable Long id) {
        try {
            assessmentRepository.deleteById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── DOCTOR ACTION: Mark assessment as reviewed to clear from queue ───
    @PatchMapping("/{id}/review")
    public ResponseEntity<?> markAsReviewed(@PathVariable Long id) {
        try {
            Assessment assessment = assessmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));

            assessment.setStatus("Reviewed");
            assessmentRepository.save(assessment);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}