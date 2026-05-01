package com.therapea.backend.controller;

import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctors")
@CrossOrigin(origins = "*")
public class DoctorController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/list")
    public ResponseEntity<?> getDoctors() {
        try {
            List<UserEntity> doctors = userRepository.findAll().stream()
                    .filter(u -> "DOCTOR".equalsIgnoreCase(u.getRole()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> responseList = new ArrayList<>();
            for (UserEntity doc : doctors) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", doc.getId().toString());
                map.put("name", "Dr. " + doc.getFullName());
                map.put("email", doc.getEmail());

                // 🔴 ADDED THIS LINE: Now React gets the Supabase image link!
                map.put("profilePictureUrl", doc.getProfilePictureUrl());

                // USE REAL DATA IF IT EXISTS
                map.put("rate", doc.getHourlyRate() != null ? doc.getHourlyRate() : 1500.0);
                map.put("bio", doc.getClinicalBio() != null ? doc.getClinicalBio() : "I am a dedicated mental health professional.");

                // Keep placeholders for things you aren't capturing in registration yet
                map.put("title", "Licensed Professional");
                map.put("rating", 5.0);
                map.put("reviews", 0);
                map.put("experience", "Verified");
                map.put("specialties", List.of("General Therapy"));
                map.put("available", true);
                map.put("online", true);

                responseList.add(map);
            }

            return ResponseEntity.ok(Map.of("success", true, "doctors", responseList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}