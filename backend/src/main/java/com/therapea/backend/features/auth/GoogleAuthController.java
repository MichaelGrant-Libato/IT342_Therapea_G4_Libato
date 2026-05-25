package com.therapea.backend.features.auth;

import com.therapea.backend.features.users.UserEntity;
import com.therapea.backend.features.users.UserService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private OTPService otpService;

    // ── FIXED ENVIRONMENT VARIABLES ──────────────────────────────────────────
    @Value("${GOOGLE_CLIENT_ID}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET}")
    private String googleClientSecret;

    @Value("${GOOGLE_REDIRECT_URI:http://localhost:8083/api/auth/google/callback}")
    private String redirectUri;

    @Value("${VITE_API_BASE_URL:http://localhost:5173}")
    private String FRONTEND_URL;

    private static final String GOOGLE_AUTH_URL     = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    // ── Step 1: Generate Google OAuth2 URL ───────────────────────────────
    @GetMapping("/google-register-url")
    public ResponseEntity<Map<String, Object>> getGoogleRegisterUrl(
            @RequestParam(value = "source", required = false) String source) {
        try {
            String scopes = URLEncoder.encode(
                    "https://www.googleapis.com/auth/userinfo.email " +
                            "https://www.googleapis.com/auth/userinfo.profile",
                    StandardCharsets.UTF_8
            );

            String baseState = UUID.randomUUID().toString();
            String state     = "android".equals(source) ? "android_" + baseState : baseState;

            String authUrl = GOOGLE_AUTH_URL + "?"
                    + "client_id="     + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&scope="        + scopes
                    + "&access_type=offline"
                    + "&prompt=consent"
                    + "&state="        + state;

            System.out.println("🔗 OAuth URL generated. redirect_uri = " + redirectUri);

            return ResponseEntity.ok(Map.of("success", true, "url", authUrl, "state", state));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", "Failed to generate OAuth URL: " + e.getMessage()
            ));
        }
    }

    // ── Step 2: Handle Google callback ───────────────────────────────────
    @GetMapping("/google/callback")
    public void googleCallback(
            @RequestParam(value = "code",  required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse httpResponse) throws IOException {

        if (error != null || code == null) {
            System.out.println("❌ OAuth cancelled or error param received: " + error);
            sendPopupMessage(httpResponse, "cancelled", null, null);
            return;
        }

        System.out.println("🔄 OAuth callback received. Exchanging code for token...");

        try {
            RestTemplate restTemplate = new RestTemplate();

            // ── Token exchange ────────────────────────────────────────────
            MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
            tokenRequest.add("code",          code);
            tokenRequest.add("client_id",     googleClientId);
            tokenRequest.add("client_secret", googleClientSecret);
            tokenRequest.add("redirect_uri",  redirectUri);
            tokenRequest.add("grant_type",    "authorization_code");

            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, Object> tokenResponse;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> tr = restTemplate.postForObject(
                        GOOGLE_TOKEN_URL,
                        new HttpEntity<>(tokenRequest, tokenHeaders),
                        Map.class
                );
                tokenResponse = tr;
            } catch (HttpClientErrorException ex) {
                System.err.println("❌ Token exchange failed: HTTP " + ex.getStatusCode());
                System.err.println("   Google error body: " + ex.getResponseBodyAsString());
                sendPopupMessage(httpResponse, "error", null, null);
                return;
            }

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                System.err.println("❌ Token response missing access_token: " + tokenResponse);
                sendPopupMessage(httpResponse, "error", null, null);
                return;
            }

            String accessToken = (String) tokenResponse.get("access_token");
            System.out.println("✅ Token exchange successful.");

            // ── Fetch user info ───────────────────────────────────────────
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
            boolean isAndroidApp   = state != null && state.startsWith("android_");

            // ── Android deep-link redirect ────────────────────────────────
            if (isAndroidApp) {
                String type = existingUser != null ? "existing" : "new";
                String redirectUrl = "therapea://login?email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&type=" + type;

                System.out.println("📱 Android OAuth — redirecting to: " + redirectUrl);

                httpResponse.setContentType("text/html;charset=UTF-8");
                httpResponse.getWriter().write(
                        "<!DOCTYPE html><html>" +
                                "<head><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>" +
                                "<body style='text-align:center;font-family:sans-serif;padding-top:20%;background:#F5F6F2;margin:0;'>" +
                                "<h2 style='color:#1C1F1A;'>Authenticating…</h2>" +
                                "<p style='color:#4A5047;'>Returning you to TheraPea…</p>" +
                                "<script>setTimeout(function(){ window.location.href='" + redirectUrl + "'; },100);</script>" +
                                "<br><br>" +
                                "<a href='" + redirectUrl + "' style='display:inline-block;padding:14px 28px;" +
                                "background:#7A9E78;color:white;text-decoration:none;border-radius:24px;font-weight:bold;'>" +
                                "Open App Now</a>" +
                                "</body></html>"
                );
                return;
            }

            // ── Web popup message ─────────────────────────────────────────
            if (existingUser != null) {
                System.out.println("🌐 Existing web user: " + email);
                sendPopupMessage(httpResponse, "existing", email, null);
            } else {
                System.out.println("🌐 New web user: " + email);
                sendPopupMessage(httpResponse, "new", email, name);
            }

        } catch (Exception e) {
            System.err.println("❌ OAuth callback exception: " + e.getClass().getName() + ": " + e.getMessage());
            sendPopupMessage(httpResponse, "error", null, null);
        }
    }

    // ── Popup postMessage helper ──────────────────────────────────────────
    private void sendPopupMessage(HttpServletResponse response,
                                  String type, String email, String name) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String emailJs = (email != null) ? "\"" + email.replace("\"", "\\\"") + "\"" : "null";
        String nameJs  = (name  != null) ? "\"" + name.replace("\"",  "\\\"") + "\"" : "null";

        response.getWriter().write("""
            <!DOCTYPE html>
            <html><body>
            <script>
              try {
                window.opener.postMessage(
                  { type: '%s', email: %s, name: %s },
                  '%s'
                );
              } catch(e) {}
              window.close();
            </script>
            </body></html>
            """.formatted(type, emailJs, nameJs, FRONTEND_URL));
    }

    // ── Complete Google profile ───────────────────────────────────────────
    @PostMapping("/complete-google-profile")
    public ResponseEntity<Map<String, Object>> completeGoogleProfile(
            @RequestBody Map<String, Object> request) {
        try {
            String email    = (String) request.get("email");
            String fullName = (String) request.get("fullName");
            String role     = (String) request.get("role");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email is required"));

            UserEntity user = userService.getUserByEmail(email);

            if (user == null) {
                user = new UserEntity();
                user.setEmail(email);
                user.setFullName(fullName != null && !fullName.isBlank() ? fullName : email);
                user.setPassword(UUID.randomUUID().toString());
                user.setEmailVerified(true);
                user.setIsActive(true);
                user.setVerifiedAt(LocalDateTime.now());
                user.setCreatedAt(LocalDateTime.now());
                user.setSessionHours(24);
            } else {
                if (fullName != null && !fullName.isBlank()) user.setFullName(fullName);
            }

            user.setRole(role != null ? role : "PATIENT");
            user.setProfileCompleted(true);
            user.setCompletedAt(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());

            UserEntity saved = userService.saveUser(user);

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
                    "success", false, "error", "Failed to complete profile: " + e.getMessage()
            ));
        }
    }

    // ── Complete standard profile ─────────────────────────────────────────
    @PostMapping("/complete-profile")
    public ResponseEntity<Map<String, Object>> completeProfile(
            @RequestBody Map<String, Object> request) {
        try {
            String email    = (String) request.get("email");
            String fullName = (String) request.get("fullName");
            String role     = (String) request.get("role");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email is required"));

            UserEntity user = userService.getUserByEmail(email);
            if (user == null)
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "User not found"));

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
                    "success", false, "error", "Failed to complete profile: " + e.getMessage()
            ));
        }
    }

    // ── Google login (after OTP) ──────────────────────────────────────────
    @PostMapping("/google-login")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody Map<String, Object> request) {
        try {
            String email = (String) request.get("email");

            if (email == null || email.isBlank())
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email is required"));

            UserEntity user = userService.getUserByEmail(email);
            if (user == null)
                return ResponseEntity.status(404).body(Map.of(
                        "success", false, "error", "User not found. Please register first."
                ));

            if ("DOCTOR".equals(user.getRole())) {
                if ("PENDING".equals(user.getStatus()))
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "success", false, "error", "Your application is still under review."));
                if ("REJECTED".equals(user.getStatus()))
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "success", false, "error", "Your application was declined. Reason: " + user.getRejectionReason()));
                if (user.getIsActive() != null && !user.getIsActive())
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "success", false, "error", "Your account is deactivated. Please contact support."));
            }

            user.setLastLogin(LocalDateTime.now());
            userService.saveUser(user);

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "userId",   user.getId().toString(),
                    "email",    user.getEmail(),
                    "fullName", user.getFullName() != null ? user.getFullName() : "",
                    "role",     user.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "error", "Login failed: " + e.getMessage()
            ));
        }
    }

    // ── Native Android Google Sign-In ─────────────────────────────────────
    @PostMapping("/google-native")
    public ResponseEntity<Map<String, Object>> googleNativeLogin(
            @RequestBody Map<String, String> request) {
        try {
            String idTokenString = request.get("idToken");

            if (idTokenString == null || idTokenString.isBlank())
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing idToken"));

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory()
            ).setAudience(Collections.singletonList(googleClientId)).build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false, "error", "Invalid Google Token"));

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name  = (String) payload.get("name");

            UserEntity user = userService.getUserByEmail(email);

            if (user == null) {
                System.out.println("📱 Native Auth: Creating new user for " + email);
                user = new UserEntity();
                user.setEmail(email);
                user.setFullName(name != null ? name : email);
                user.setRole("PATIENT");
                user.setPassword(UUID.randomUUID().toString());
                user.setEmailVerified(true);
                user.setIsActive(true);
                user.setVerifiedAt(LocalDateTime.now());
                user.setCreatedAt(LocalDateTime.now());
                user.setSessionHours(24);
            } else {
                System.out.println("📱 Native Auth: Logging in existing user " + email);

                if ("DOCTOR".equals(user.getRole())) {
                    if ("PENDING".equals(user.getStatus()))
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                "success", false, "error", "Your application is still under review."));
                    if ("REJECTED".equals(user.getStatus()))
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                "success", false, "error", "Your application was declined."));
                    if (user.getIsActive() != null && !user.getIsActive())
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                                "success", false, "error", "Your account is deactivated."));
                }
            }

            user.setLastLogin(LocalDateTime.now());
            UserEntity saved = userService.saveUser(user);

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "userId",   saved.getId().toString(),
                    "email",    saved.getEmail(),
                    "fullName", saved.getFullName() != null ? saved.getFullName() : "",
                    "role",     saved.getRole()
            ));
        } catch (Exception e) {
            System.err.println("❌ Native OAuth exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false, "error", "Authentication error: " + e.getMessage()
            ));
        }
    }

    // ── Debug endpoint ────────────────────────────────────────────────────
    @GetMapping("/debug-config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        return ResponseEntity.ok(Map.of(
                "clientId",     googleClientId     != null ? googleClientId.substring(0, Math.min(20, googleClientId.length())) + "..." : "NULL",
                "clientSecret", googleClientSecret != null ? "SET (" + googleClientSecret.length() + " chars)" : "NULL",
                "redirectUri",  redirectUri        != null ? redirectUri : "NULL"
        ));
    }
}