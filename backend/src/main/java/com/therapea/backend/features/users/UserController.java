package com.therapea.backend.features.users;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    // ─── 1. PROFILE UPDATE (Picture, Schedule, Expectations, Bio, etc.) ───
    @PatchMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody UserRegistrationDTO dto) {
        try {
            // Check if the email exists in the DTO
            if (dto.getEmail() == null || dto.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email is required to update profile"));
            }

            // Call the robust update method in the service
            UserEntity updatedUser = userService.updateUserProfile(dto.getEmail(), dto);

            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 2. CHANGE PASSWORD (SETTINGS) ───
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String oldPassword = payload.get("oldPassword");
            String newPassword = payload.get("newPassword");

            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "New password must be at least 6 characters."));
            }

            userService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password updated successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}