package com.therapea.backend.controller;

import com.therapea.backend.dto.DashboardDTO;
import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            userService.registerUser(registrationDTO);
            return ResponseEntity.ok("User registered successfully!");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO) {
        try {
            System.out.println("Attempting login for email: " + loginDTO.getEmail());
            UserEntity user = userService.loginUser(loginDTO);
            DashboardDTO response = mapToDTO(user, "Login successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Catching Exception grabs EVERYTHING
            System.out.println("====== LOGIN REJECTED ======");
            System.out.println("Reason: " + e.getMessage());
            e.printStackTrace(System.out);

            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
        }
        try {
            UserEntity user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }
            return ResponseEntity.ok(mapToDTO(user, "User found"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User record not found");
        }
    }

    @PostMapping("/doctor-verification")
    public ResponseEntity<Map<String, Object>> doctorVerification(
            @RequestParam("email")       String email,
            @RequestParam("clinicalBio") String clinicalBio,
            @RequestParam("hourlyRate")  String hourlyRate,
            @RequestParam("prcLicense")  MultipartFile prcLicense) {
        try {
            System.out.println("📋 Doctor verification received for: " + email);
            System.out.println("   Bio: "  + clinicalBio);
            System.out.println("   Rate: " + hourlyRate);
            System.out.println("   File: " + prcLicense.getOriginalFilename() +
                    " (" + prcLicense.getSize() + " bytes)");

            // TODO: save file + metadata to DB/storage

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification submitted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    private DashboardDTO mapToDTO(UserEntity user, String message) {
        DashboardDTO dto = new DashboardDTO();
        dto.setUserId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setMessage(message);
        return dto;
    }
}