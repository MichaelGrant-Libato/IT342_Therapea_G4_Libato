package com.therapea.backend.controller;

import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import com.therapea.backend.service.EmailNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailNotificationService emailNotificationService;

    @GetMapping("/doctors")
    public ResponseEntity<?> getAllDoctors() {
        List<UserEntity> doctors = userRepository.findByRole("DOCTOR");

        List<Map<String, Object>> doctorResponses = doctors.stream().map(doc -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", doc.getId().toString());
            map.put("fullName", doc.getFullName());
            map.put("email", doc.getEmail());
            map.put("clinicalBio", doc.getClinicalBio() != null ? doc.getClinicalBio() : "No bio provided.");
            map.put("hourlyRate", doc.getHourlyRate() != null ? doc.getHourlyRate() : 0.0);
            map.put("status", doc.getStatus() != null ? doc.getStatus() : "PENDING");
            map.put("prcLicenseUrl", "#");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(doctorResponses);
    }

    // ✅ ADDED: Endpoint to fetch and display the PRC Document
    @GetMapping("/doctors/{id}/prc-license")
    public ResponseEntity<byte[]> getPrcLicense(@PathVariable UUID id) {
        UserEntity doctor = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        byte[] fileData = doctor.getPrcLicenseData();

        // Return a 404 cleanly if the file is missing (e.g., old accounts)
        if (fileData == null) {
            return ResponseEntity.notFound().build();
        }

        // Get the file type, fallback to a generic binary stream if unknown
        String contentType = doctor.getPrcLicenseType() != null
                ? doctor.getPrcLicenseType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"prc-license-" + id + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileData);
    }

    @PostMapping("/doctors/{id}/approve")
    public ResponseEntity<?> approveDoctor(@PathVariable UUID id) {
        UserEntity doctor = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Doctor not found"));

        doctor.setStatus("APPROVED");
        doctor.setIsActive(true);
        doctor.setRejectionReason(null);

        userRepository.save(doctor);

        if (emailNotificationService != null) {
            emailNotificationService.sendApplicationApproved(doctor.getEmail(), doctor.getFullName());
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Doctor approved"));
    }

    @PostMapping("/doctors/{id}/reject")
    public ResponseEntity<?> rejectDoctor(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        UserEntity doctor = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        String reason = request.getOrDefault("reason", "Your application did not meet our platform requirements.");

        doctor.setStatus("REJECTED");
        doctor.setRejectionReason(reason);
        userRepository.save(doctor);

        if (emailNotificationService != null) {
            emailNotificationService.sendApplicationRejected(doctor.getEmail(), doctor.getFullName(), reason);
        } else {
            System.out.println("📧 EMAIL TO " + doctor.getEmail() + " | REASON: " + reason);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Doctor rejected and reason saved for tracking."));
    }
}