package com.therapea.backend.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.therapea.backend.dto.DashboardDTO;
import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.UserService;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    private final Dotenv dotenv = Dotenv.load();
    private final String GOOGLE_CLIENT_ID = dotenv.get("GOOGLE_CLIENT_ID");

    @PostMapping("/google-check")
    public ResponseEntity<?> googleCheck(@RequestBody Map<String, String> request) {
        String idTokenString = request.get("idToken");

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();

                // ✅ FIX: Use getUserByEmail to allow the "else" block to work
                UserEntity user = userService.getUserByEmail(email);
                if (user != null) {
                    return ResponseEntity.ok(mapToDTO(user, "Google Login successful"));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                            "email", email,
                            "fullName", (String) payload.get("name"),
                            "message", "User not found. Please complete registration."
                    ));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token verification failed: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google Token");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            UserEntity user = userService.registerUser(registrationDTO);
            return ResponseEntity.ok(mapToDTO(user, "User registered successfully!"));
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
        }
        try {
            UserEntity user = userService.findByEmail(email);
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