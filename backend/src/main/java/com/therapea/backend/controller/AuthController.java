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
            UserEntity user = userService.loginUser(loginDTO);
            return ResponseEntity.ok(mapToDTO(user, "Login successful"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam String email) {
        // ✅ FIX: Use getUserByEmail for a cleaner 404 check
        UserEntity user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User record not found");
        }
        return ResponseEntity.ok(mapToDTO(user, "User found"));
    }

    @PostMapping("/doctor-verification")
    public ResponseEntity<Map<String, Object>> doctorVerification(
            @RequestParam("email") String email,
            @RequestParam("clinicalBio") String bio,
            @RequestParam("hourlyRate") String rate,
            @RequestParam("prcLicense") MultipartFile file) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Verification submitted"));
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