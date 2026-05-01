package com.therapea.backend.controller;

import com.therapea.backend.entity.AppointmentEntity;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.service.AppointmentService;
import com.therapea.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserService userService;

    // ─── 1. BOOK APPOINTMENT ───
    @PostMapping("/book")
    public ResponseEntity<?> bookAppointment(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            UserEntity patient = userService.findByEmail(email);

            AppointmentEntity apt = new AppointmentEntity();
            apt.setUserId(patient.getId());
            apt.setPatientName(patient.getFullName());

            apt.setProviderId(payload.get("providerId"));
            apt.setProviderName(payload.get("providerName"));
            apt.setProviderEmail(payload.get("providerEmail"));
            apt.setDisplayDate(payload.get("date"));
            apt.setDisplayTime(payload.get("time"));
            apt.setAppointmentType(payload.get("type"));

            apt.setStatus("Scheduled");
            apt.setToday(false);

            appointmentService.saveAppointment(apt);

            return ResponseEntity.ok(Map.of("success", true, "message", "Appointment booked successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 2. GET APPOINTMENTS ───
    @GetMapping("/user")
    public ResponseEntity<?> getUserAppointments(@RequestParam String email) {
        try {
            UserEntity user = userService.findByEmail(email);
            List<AppointmentEntity> allAppointments = appointmentService.getAllAppointments();
            List<AppointmentEntity> userAppointments;

            if ("DOCTOR".equalsIgnoreCase(user.getRole())) {
                userAppointments = allAppointments.stream()
                        .filter(a -> a.getProviderName() != null && a.getProviderName().contains(user.getFullName()))
                        .collect(Collectors.toList());
            } else {
                userAppointments = appointmentService.getAppointmentsByUserId(user.getId());
            }

            List<Map<String, Object>> responseList = new ArrayList<>();
            for (AppointmentEntity e : userAppointments) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("date", e.getDisplayDate());
                map.put("time", e.getDisplayTime());
                map.put("type", e.getAppointmentType());
                map.put("status", e.getStatus() != null ? e.getStatus() : "Scheduled");

                map.put("providerId", e.getProviderId());
                map.put("providerName", e.getProviderName());
                map.put("providerEmail", e.getProviderEmail());

                // 🔴 NEW LOGIC: Look up the Doctor's profile picture using their email!
                if (e.getProviderEmail() != null) {
                    UserEntity doctor = userService.getUserByEmail(e.getProviderEmail());
                    map.put("providerProfilePictureUrl", doctor != null ? doctor.getProfilePictureUrl() : null);
                } else {
                    map.put("providerProfilePictureUrl", null);
                }

                map.put("patientName", e.getPatientName());

                responseList.add(map);
            }

            return ResponseEntity.ok(Map.of("success", true, "appointments", responseList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            Optional<AppointmentEntity> aptOpt = appointmentService.getAppointmentById(id);
            if (aptOpt.isPresent()) {
                AppointmentEntity apt = aptOpt.get();
                apt.setStatus("Canceled");
                apt.setCancelReason(payload.get("reason"));

                appointmentService.saveAppointment(apt);
                return ResponseEntity.ok(Map.of("success", true, "message", "Appointment canceled successfully"));
            } else {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Appointment not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAppointment(@PathVariable Long id) {
        try {
            if (appointmentService.deleteAppointment(id)) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Appointment deleted"));
            } else {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/provider/{providerName}")
    public ResponseEntity<?> getProviderAppointments(@PathVariable String providerName) {
        try {
            List<AppointmentEntity> allAppointments = appointmentService.getAllAppointments();

            List<Map<String, Object>> responseList = allAppointments.stream()
                    .filter(a -> a.getProviderName() != null && a.getProviderName().equalsIgnoreCase(providerName))
                    .map(e -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", e.getId());
                        map.put("date", e.getDisplayDate());
                        map.put("time", e.getDisplayTime());
                        map.put("status", e.getStatus());
                        map.put("assessmentType", e.getAssessmentType());
                        map.put("notes", e.getNotes());
                        return map;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("success", true, "appointments", responseList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}