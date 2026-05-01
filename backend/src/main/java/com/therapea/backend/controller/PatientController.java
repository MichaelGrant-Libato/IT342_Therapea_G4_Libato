package com.therapea.backend.controller;

import com.therapea.backend.entity.AppointmentEntity;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.AppointmentRepository;
import com.therapea.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin(origins = "*")
public class PatientController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @GetMapping("/doctor")
    public ResponseEntity<?> getDoctorPatients(@RequestParam String email) {
        try {
            // 1. Find the logged-in doctor
            UserEntity doctor = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .findFirst()
                    .orElseThrow(() -> new Exception("Doctor not found"));

            // 2. Find all appointments in the system
            List<AppointmentEntity> allApts = appointmentRepository.findAll();

            // 3. Filter using the providerName field
            List<AppointmentEntity> docApts = allApts.stream()
                    .filter(a -> a.getProviderName() != null &&
                            a.getProviderName().contains(doctor.getFullName()))
                    .collect(Collectors.toList());

            // 4. Extract unique Patient IDs from those appointments
            Set<UUID> patientIds = docApts.stream()
                    .map(AppointmentEntity::getUserId)
                    .collect(Collectors.toSet());

            // 5. Build the patient roster
            List<Map<String, Object>> responseList = new ArrayList<>();

            for (UUID patientId : patientIds) {
                Optional<UserEntity> patientOpt = userRepository.findById(patientId);

                if (patientOpt.isPresent()) {
                    UserEntity patient = patientOpt.get();

                    // Find appointments specifically between this doctor and this patient
                    List<AppointmentEntity> specificApts = docApts.stream()
                            .filter(a -> a.getUserId().equals(patientId))
                            .collect(Collectors.toList());

                    String status = specificApts.isEmpty() ? "New Patient" : "Active";
                    String nextSession = specificApts.isEmpty() ? "None" : specificApts.get(0).getDisplayDate();

                    Map<String, Object> map = new HashMap<>();
                    map.put("id", patient.getId().toString());
                    map.put("name", patient.getFullName());
                    map.put("email", patient.getEmail());
                    map.put("status", status);
                    map.put("lastSession", "None");
                    map.put("nextSession", nextSession);
                    map.put("risk", "Pending");

                    // 🔴 THIS IS THE LINE THAT MAKES IT WORK FOR DOCTORS!
                    map.put("profilePictureUrl", patient.getProfilePictureUrl());

                    responseList.add(map);
                }
            }

            return ResponseEntity.ok(Map.of("success", true, "patients", responseList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}