package com.therapea.backend.features.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final Map<String, Map<String, Object>> userSettings = new ConcurrentHashMap<>();

    @GetMapping
    public ResponseEntity<?> getSettings(@RequestParam String email) {
        Map<String, Object> settings = userSettings.getOrDefault(email, Map.of(
                "emailAlerts", true,
                "smsAlerts", false,
                "marketingEmails", false,
                "language", "English (US)",
                "timezone", "Asia/Manila (PHT)",
                "theme", "Light"
        ));

        return ResponseEntity.ok(Map.of("success", true, "settings", settings));
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email is required"));
        }

        userSettings.put(email, payload);

        return ResponseEntity.ok(Map.of("success", true));
    }
}