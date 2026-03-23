package com.therapea.backend.controller;

import com.therapea.backend.service.OTPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class OTPController {

    @Autowired
    private OTPService otpService;

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOTP(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Email is required"
            ));
        }

        try {
            String otp = otpService.generateOTP();
            otpService.storeOTP(email, otp);

            // OTP is returned in response since no email sending
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP generated",
                    "otp", otp  // remove this in production, deliver via another channel
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to generate OTP: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOTP(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Email and OTP are required"
            ));
        }

        try {
            boolean isValid = otpService.verifyOTP(email, otp);

            if (isValid) {
                otpService.clearOTP(email);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Email verified successfully!"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid OTP or OTP has expired"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to verify OTP: " + e.getMessage()
            ));
        }
    }
}