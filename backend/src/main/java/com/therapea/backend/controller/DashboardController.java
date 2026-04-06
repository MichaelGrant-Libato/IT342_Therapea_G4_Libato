package com.therapea.backend.controller;

import com.therapea.backend.dto.DashboardResponseDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import com.therapea.backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam String email) {
        try {
            // Your UserRepository returns an Optional
            Optional<UserEntity> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                Map<String, Object> errorResp = new HashMap<>();
                errorResp.put("success", false);
                errorResp.put("error", "User not found");
                return ResponseEntity.status(404).body(errorResp);
            }

            UserEntity user = userOpt.get();

            // Pass the user to the service to build the dynamic dashboard payload
            DashboardResponseDTO dashboardData = dashboardService.getDashboardData(user);
            return ResponseEntity.ok(dashboardData);

        } catch (Exception e) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("error", "Server error: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResp);
        }
    }
}