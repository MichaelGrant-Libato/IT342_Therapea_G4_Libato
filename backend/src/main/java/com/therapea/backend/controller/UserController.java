package com.therapea.backend.controller;

import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.UserService;
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

    // ─── 1. PROFILE PICTURE UPDATE ───
    @PatchMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> payload) {
        try {
            String email = (String) payload.get("email");
            UserEntity user = userService.getUserByEmail(email);

            if (user != null) {
                if (payload.containsKey("profilePictureUrl")) {
                    user.setProfilePictureUrl((String) payload.get("profilePictureUrl"));
                }

                userService.saveUser(user);
                return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 2. CHANGE PASSWORD (SETTINGS) ───
    // 🔴 NEW: Endpoint for the Settings page to change password
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String oldPassword = payload.get("oldPassword");
            String newPassword = payload.get("newPassword");

            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "New password must be at least 6 characters."));
            }

            // Call the new method we just added to UserService!
            userService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password updated successfully!"));
        } catch (Exception e) {
            // This catches the "Incorrect current password" or "User not found" errors
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}