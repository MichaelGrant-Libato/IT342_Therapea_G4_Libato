package com.therapea.backend.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.therapea.backend.dto.DashboardDTO;
import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import com.therapea.backend.service.EmailNotificationService;
import com.therapea.backend.service.OTPService;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailNotificationService emailNotificationService;

    @Autowired
    private OTPService otpService;

    private final Dotenv dotenv = Dotenv.load();
    private final String GOOGLE_CLIENT_ID = dotenv.get("GOOGLE_CLIENT_ID");

    // ==========================================
    // OTP SERVICES (USED FOR GOOGLE FLOW)
    // ==========================================
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String type = request.getOrDefault("type", "REGISTER");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        try {
            UserEntity existingUser = userService.getUserByEmail(email);

            if ("REGISTER".equalsIgnoreCase(type) && existingUser != null) {
                if (!"REJECTED".equals(existingUser.getStatus())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered. Please sign in."));
                }
            }

            if ("LOGIN".equalsIgnoreCase(type) && existingUser == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Account not found. Please register first."));
            }

            otpService.generateAndSendOtp(email, type);
            return ResponseEntity.ok(Map.of("success", true, "message", "OTP sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to send verification email."));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));
        }

        boolean isValid = otpService.verifyOtp(email, otp);

        if (isValid) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Email verified successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid or expired verification code"));
        }
    }

    // ==========================================
    // PATIENT REGISTRATION ENDPOINTS
    // ==========================================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            UserEntity user = userService.registerUser(registrationDTO);
            return ResponseEntity.ok(mapToDTO(user, "User registered successfully!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/finalize-google-profile")
    public ResponseEntity<?> finalizeGoogleProfile(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            if (!otpService.isEmailVerified(registrationDTO.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Email address has not been verified via OTP."));
            }

            UserEntity user = userService.registerUser(registrationDTO);
            otpService.clearVerification(registrationDTO.getEmail());

            return ResponseEntity.ok(mapToDTO(user, "Google profile completed!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // DOCTOR REGISTRATION & VERIFICATION (ATOMIC)
    // ==========================================

    @PostMapping("/register-doctor")
    public ResponseEntity<?> registerDoctor(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("clinicalBio") String clinicalBio,
            @RequestParam("hourlyRate") String hourlyRate,
            @RequestParam(value = "prcLicense", required = false) MultipartFile prcLicense) {
        try {
            if (prcLicense == null || prcLicense.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "PRC License document is required."));
            }

            UserEntity existingUser = userService.getUserByEmail(email);
            if (existingUser != null) {
                if ("REJECTED".equals(existingUser.getStatus())) {
                    userRepository.delete(existingUser);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
                }
            }

            UserRegistrationDTO dto = new UserRegistrationDTO();
            dto.setFullName(fullName);
            dto.setEmail(email);
            dto.setPassword(password);
            dto.setRole("DOCTOR");

            UserEntity user = userService.registerUser(dto);

            user.setClinicalBio(clinicalBio);
            user.setHourlyRate(Double.parseDouble(hourlyRate));
            user.setStatus("PENDING");

            // ✅ ADDED: Save the actual file bytes and type into the database!
            user.setPrcLicenseData(prcLicense.getBytes());
            user.setPrcLicenseType(prcLicense.getContentType());

            String refNumber = "TRK-" + (int)(Math.random() * 900000 + 100000);
            user.setReferenceNumber(refNumber);

            userService.saveUser(user);

            return ResponseEntity.ok(Map.of("success", true, "referenceNumber", refNumber, "message", "Doctor registered successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred during registration."));
        }
    }

    @PostMapping("/register-google-doctor")
    public ResponseEntity<?> registerGoogleDoctor(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("clinicalBio") String clinicalBio,
            @RequestParam("hourlyRate") String hourlyRate,
            @RequestParam(value = "prcLicense", required = false) MultipartFile prcLicense) {
        try {
            if (!otpService.isEmailVerified(email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Email address has not been verified via OTP."));
            }
            if (prcLicense == null || prcLicense.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "PRC License document is required."));
            }

            UserEntity existingUser = userService.getUserByEmail(email);
            if (existingUser != null) {
                if ("REJECTED".equals(existingUser.getStatus())) {
                    userRepository.delete(existingUser);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
                }
            }

            UserRegistrationDTO dto = new UserRegistrationDTO();
            dto.setFullName(fullName);
            dto.setEmail(email);
            dto.setPassword("");
            dto.setRole("DOCTOR");

            UserEntity user = userService.registerUser(dto);
            otpService.clearVerification(email);

            user.setClinicalBio(clinicalBio);
            user.setHourlyRate(Double.parseDouble(hourlyRate));
            user.setStatus("PENDING");

            // ✅ ADDED: Save the actual file bytes and type into the database!
            user.setPrcLicenseData(prcLicense.getBytes());
            user.setPrcLicenseType(prcLicense.getContentType());

            String refNumber = "TRK-" + (int)(Math.random() * 900000 + 100000);
            user.setReferenceNumber(refNumber);

            userService.saveUser(user);

            if (emailNotificationService != null) {
                emailNotificationService.sendApplicationReceived(email, user.getFullName());
            }

            return ResponseEntity.ok(Map.of("success", true, "referenceNumber", refNumber, "message", "Google Doctor registered successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred during registration."));
        }
    }

    // ==========================================
    // LOGIN & AUTHENTICATION ENDPOINTS
    // ==========================================

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

                UserEntity user = userService.getUserByEmail(email);
                if (user != null) {
                    if ("DOCTOR".equals(user.getRole())) {
                        if ("PENDING".equals(user.getStatus())) {
                            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                    "error", "Your application is still under review. Please wait for an approval email."));
                        }
                        if ("REJECTED".equals(user.getStatus())) {
                            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                    "error", "Your application was declined. Reason: " + user.getRejectionReason()));
                        }
                        if (user.getIsActive() != null && !user.getIsActive()) {
                            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                    "error", "Your account is currently deactivated. Please contact support."));
                        }
                    }
                    return ResponseEntity.ok(mapToDTO(user, "Google Login successful"));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                            "email", email,
                            "fullName", (String) payload.get("name"),
                            "error", "User not found. Please complete registration."
                    ));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token verification failed."));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Google Token"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO) {
        try {
            UserEntity user = userService.loginUser(loginDTO);

            if ("DOCTOR".equals(user.getRole())) {
                if ("PENDING".equals(user.getStatus())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "error", "Your application is still under review. Please wait for an approval email."));
                }
                if ("REJECTED".equals(user.getStatus())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "error", "Your application was declined. Reason: " + user.getRejectionReason()));
                }
                if (user.getIsActive() != null && !user.getIsActive()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "error", "Your account is currently deactivated. Please contact support."));
                }
            }

            DashboardDTO response = mapToDTO(user, "Login successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
        }
        try {
            UserEntity user = userService.findByEmail(email);
            return ResponseEntity.ok(mapToDTO(user, "User found"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User record not found"));
        }
    }

    @GetMapping("/check-status")
    public ResponseEntity<?> checkStatus(@RequestParam("ref") String referenceNumber, @RequestParam("email") String email) {
        try {
            UserEntity user = userService.getUserByEmail(email);
            if (user != null && referenceNumber.equals(user.getReferenceNumber())) {
                return ResponseEntity.ok(Map.of(
                        "status", user.getStatus() != null ? user.getStatus() : "PENDING",
                        "message", user.getRejectionReason() != null ? user.getRejectionReason() : ""
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No application found with these details."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An error occurred while checking status."));
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