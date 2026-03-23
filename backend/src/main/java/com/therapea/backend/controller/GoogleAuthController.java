package com.therapea.backend.controller;

import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.OTPService;
import com.therapea.backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    @Autowired private UserService userService;
    @Autowired private OTPService otpService;

    @Value("${google.client.id}")     private String googleClientId;
    @Value("${google.client.secret}") private String googleClientSecret;
    @Value("${google.redirect.uri}")  private String redirectUri;

    private static final String GOOGLE_AUTH_URL     = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String FRONTEND_URL        = "http://localhost:5173";

    // ─────────────────────────────────────────────
    // Step 1: Generate Google OAuth2 authorization URL
    // ─────────────────────────────────────────────
    @GetMapping("/google-register-url")
    public ResponseEntity<Map<String, Object>> getGoogleRegisterUrl() {
        try {
            String scopes = URLEncoder.encode(
                    "https://www.googleapis.com/auth/userinfo.email " +
                            "https://www.googleapis.com/auth/userinfo.profile",
                    StandardCharsets.UTF_8
            );
            String state = UUID.randomUUID().toString();

            String authUrl = GOOGLE_AUTH_URL + "?" +
                    "client_id="     + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&response_type=code" +
                    "&scope="        + scopes +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    "&state="        + state;

            return ResponseEntity.ok(Map.of("success", true, "url", authUrl, "state", state));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to generate OAuth URL: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────
    // Step 2: Handle Google OAuth2 callback
    // Returns HTML that postMessages result to parent window (popup flow)
    // ─────────────────────────────────────────────
    @GetMapping("/google/callback")
    public void googleCallback(
            @RequestParam(value = "code",  required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse httpResponse) throws IOException {

        if (error != null || code == null) {
            sendPopupMessage(httpResponse, "cancelled", null, null);
            return;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();

            // ✅ Form-encoded token exchange
            MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
            tokenRequest.add("code",          code);
            tokenRequest.add("client_id",     googleClientId);
            tokenRequest.add("client_secret", googleClientSecret);
            tokenRequest.add("redirect_uri",  redirectUri);
            tokenRequest.add("grant_type",    "authorization_code");

            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(
                    GOOGLE_TOKEN_URL,
                    new HttpEntity<>(tokenRequest, tokenHeaders),
                    Map.class
            );

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                System.err.println("❌ Token response missing access_token: " + tokenResponse);
                sendPopupMessage(httpResponse, "error", null, null);
                return;
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // ✅ Get user info via Bearer header
            HttpHeaders userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(accessToken);

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.exchange(
                    GOOGLE_USERINFO_URL, HttpMethod.GET,
                    new HttpEntity<>(userInfoHeaders), Map.class
            ).getBody();

            if (userInfo == null || !userInfo.containsKey("email")) {
                System.err.println("❌ User info missing email: " + userInfo);
                sendPopupMessage(httpResponse, "error", null, null);
                return;
            }

            String email = (String) userInfo.get("email");
            String name  = userInfo.containsKey("name") ? (String) userInfo.get("name") : email;

            System.out.println("✅ Google OAuth success for: " + email);

            UserEntity existingUser = userService.getUserByEmail(email);

            if (existingUser != null) {
                // Existing user — tell popup to trigger OTP login flow
                System.out.println("ℹ️ Existing user: " + email);
                sendPopupMessage(httpResponse, "existing", email, null);
            } else {
                // New user — tell popup to show profile completion
                System.out.println("ℹ️ New user: " + email);
                sendPopupMessage(httpResponse, "new", email, name);
            }

        } catch (Exception e) {
            System.err.println("❌ OAuth callback exception: " + e.getMessage());
            e.printStackTrace();
            sendPopupMessage(httpResponse, "error", null, null);
        }
    }

    // ─────────────────────────────────────────────
    // Sends result back to the opener window via postMessage and closes popup
    // ─────────────────────────────────────────────
    private void sendPopupMessage(HttpServletResponse response,
                                  String type,
                                  String email,
                                  String name) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String emailJs = (email != null) ? "\"" + email.replace("\"", "\\\"") + "\"" : "null";
        String nameJs  = (name  != null) ? "\"" + name.replace("\"",  "\\\"") + "\"" : "null";

        response.getWriter().write("""
            <!DOCTYPE html>
            <html>
            <body>
            <script>
              try {
                window.opener.postMessage(
                  { type: '%s', email: %s, name: %s },
                  '%s'
                );
              } catch(e) {}
              window.close();
            </script>
            </body>
            </html>
            """.formatted(type, emailJs, nameJs, FRONTEND_URL));
    }

    // ─────────────────────────────────────────────
    // Step 3: Complete profile after Google registration
    // Creates user if not exists, updates if exists
    // ─────────────────────────────────────────────
    @PostMapping("/complete-google-profile")
    public ResponseEntity<Map<String, Object>> completeGoogleProfile(
            @RequestBody Map<String, Object> request) {
        try {
            String email    = (String) request.get("email");
            String fullName = (String) request.get("fullName");
            String role     = (String) request.get("role");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "Email is required"
                ));

            UserEntity user = userService.getUserByEmail(email);

            if (user == null) {
                // First time — create user now
                user = new UserEntity();
                user.setEmail(email);
                user.setFullName(fullName != null && !fullName.isBlank() ? fullName : email);
                user.setPassword(UUID.randomUUID().toString());
                user.setEmailVerified(true);
                user.setActive(true);
                user.setVerifiedAt(LocalDateTime.now());
                user.setCreatedAt(LocalDateTime.now());
                user.setSessionHours(24);
                System.out.println("✅ Creating new user on profile completion: " + email);
            } else {
                if (fullName != null && !fullName.isBlank()) user.setFullName(fullName);
                System.out.println("✅ Updating existing user profile: " + email);
            }

            user.setRole(role != null ? role : "PATIENT");
            user.setProfileCompleted(true);
            user.setCompletedAt(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());

            UserEntity saved = userService.saveUser(user);
            System.out.println("✅ Profile saved for: " + email);

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "message",  "Profile completed successfully!",
                    "userId",   saved.getId().toString(),
                    "email",    saved.getEmail(),
                    "fullName", saved.getFullName(),
                    "role",     saved.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to complete profile: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────
    // Complete profile (standard — email + name + role)
    // ─────────────────────────────────────────────
    @PostMapping("/complete-profile")
    public ResponseEntity<Map<String, Object>> completeProfile(
            @RequestBody Map<String, Object> request) {
        try {
            String email    = (String) request.get("email");
            String fullName = (String) request.get("fullName");
            String role     = (String) request.get("role");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "Email is required"
                ));

            UserEntity user = userService.getUserByEmail(email);
            if (user == null)
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "User not found"
                ));

            if (fullName != null && !fullName.isBlank()) user.setFullName(fullName);
            user.setRole(role != null ? role : "PATIENT");
            user.setProfileCompleted(true);
            user.setCompletedAt(LocalDateTime.now());

            UserEntity saved = userService.saveUser(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile completed successfully!",
                    "userId",  saved.getId().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to complete profile: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────
    // Google Login — called after OTP verified
    // Updates last login and returns session data
    // ─────────────────────────────────────────────
    @PostMapping("/google-login")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody Map<String, Object> request) {
        try {
            String email = (String) request.get("email");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "Email is required"
                ));

            UserEntity user = userService.getUserByEmail(email);
            if (user == null)
                return ResponseEntity.status(404).body(Map.of(
                        "success", false, "error", "User not found. Please register first."
                ));

            user.setLastLogin(LocalDateTime.now());
            userService.saveUser(user);
            System.out.println("✅ Google login session created for: " + email);

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "userId",   user.getId().toString(),
                    "email",    user.getEmail(),
                    "fullName", user.getFullName() != null ? user.getFullName() : "",
                    "role",     user.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Login failed: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────
    // Debug — verify env vars loaded correctly
    // ─────────────────────────────────────────────
    @GetMapping("/debug-config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        return ResponseEntity.ok(Map.of(
                "clientId",     googleClientId != null ? googleClientId.substring(0, 10) + "..." : "NULL",
                "clientSecret", googleClientSecret != null ? "SET (" + googleClientSecret.length() + " chars)" : "NULL",
                "redirectUri",  redirectUri != null ? redirectUri : "NULL"
        ));
    }
}