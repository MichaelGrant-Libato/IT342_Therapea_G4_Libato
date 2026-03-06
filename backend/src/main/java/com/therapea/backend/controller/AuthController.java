package com.therapea.backend.controller;

import com.therapea.backend.dto.DashboardDTO;
import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
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
            DashboardDTO response = new DashboardDTO();
            response.setUserId(user.getId());
            response.setFullName(user.getFullName());
            response.setRole(user.getRole());
            response.setMessage("Login successful");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}