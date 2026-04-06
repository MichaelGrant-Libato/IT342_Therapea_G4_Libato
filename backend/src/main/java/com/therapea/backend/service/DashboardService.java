package com.therapea.backend.service;

import com.therapea.backend.dto.*;
import com.therapea.backend.entity.*;
import com.therapea.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired private StatRepository statRepo;
    @Autowired private AppointmentRepository appointmentRepo;
    @Autowired private ProgressRepository progressRepo;

    public DashboardResponseDTO getDashboardData(UserEntity user) {
        DashboardResponseDTO response = new DashboardResponseDTO();

        // Map user profile details
        response.success = true;
        response.userId = user.getId().toString();
        response.email = user.getEmail();
        response.fullName = user.getFullName();
        response.role = user.getRole();
        response.emailVerified = user.isEmailVerified();
        response.profileCompleted = user.isProfileCompleted();
        response.createdAt = user.getCreatedAt() != null ? user.getCreatedAt().toString() : null;
        response.lastLogin = user.getLastLogin() != null ? user.getLastLogin().toString() : null;

        UUID userId = user.getId();

        // 1. Fetch & Map Stats
        List<StatEntity> statEntities = statRepo.findByUserId(userId);
        response.stats = statEntities.stream()
                .map(s -> new StatDTO(s.getIconKey(), s.getColor(), s.getStatValue(), s.getLabel(), s.getChangeStr(), s.isPositive()))
                .collect(Collectors.toList());

        // 2. Fetch & Map Appointments
        List<AppointmentEntity> apptEntities = appointmentRepo.findByUserId(userId);
        response.sessions = apptEntities.stream()
                .map(a -> {
                    List<TagDTO> tags = a.getTags().stream()
                            .map(t -> new TagDTO(t.getLabel(), t.getCssClass()))
                            .collect(Collectors.toList());
                    return new SessionDTO(a.getDisplayDate(), a.getDisplayTime(), a.getProviderOrPatientName(), a.getAppointmentType(), tags, a.isToday());
                }).collect(Collectors.toList());

        // 3. Fetch & Map Progress
        List<ProgressEntity> progEntities = progressRepo.findByUserId(userId);
        response.progressItems = progEntities.stream()
                .map(p -> new ProgressDTO(p.getLabel(), p.getProgressValue(), p.getCssClass()))
                .collect(Collectors.toList());

        return response;
    }
}