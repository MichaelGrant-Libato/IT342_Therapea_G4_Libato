package com.therapea.backend.features.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository repository;

    @GetMapping
    public ResponseEntity<?> getUserNotifications(@RequestParam String email) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "notifications", repository.findByUserEmailOrderByCreatedAtDesc(email)
        ));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        repository.findById(id).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@RequestParam String email) {
        repository.findByUserEmailOrderByCreatedAtDesc(email).forEach(n -> {
            n.setRead(true);
            repository.save(n);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }
}