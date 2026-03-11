package com.therapea.backend.controller;

import com.therapea.backend.dto.DashboardDTO;
import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

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
            UserEntity user = userService.loginUser(loginDTO);
            DashboardDTO response = mapToDTO(user, "Login successful");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * This endpoint is called by React to check if a user is logged in via Google (OAuth2)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            String email = principal.getAttribute("email");
            UserEntity user = userService.findByEmail(email);
            return ResponseEntity.ok(mapToDTO(user, "OAuth2 Sync successful"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User record not found");
        }
    }

    private DashboardDTO mapToDTO(UserEntity user, String message) {
        DashboardDTO dto = new DashboardDTO();
        dto.setUserId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setRole(user.getRole());
        dto.setMessage(message);
        return dto;
    }
}