package com.therapea.backend.features.notifications;

import com.therapea.backend.features.appointments.AppointmentEntity;
import com.therapea.backend.features.appointments.AppointmentRepository;
import com.therapea.backend.features.users.UserEntity;
import com.therapea.backend.features.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class AppointmentReminderScheduler {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @Scheduled(fixedRate = 900000)
    public void checkUpcomingAppointments() {
        System.out.println("Checking for upcoming appointments...");

        List<AppointmentEntity> activeAppts = appointmentRepository.findAll().stream()
                .filter(a -> "Scheduled".equals(a.getStatus()))
                .toList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);
        LocalDateTime now = LocalDateTime.now();

        for (AppointmentEntity apt : activeAppts) {
            try {
                String dateString = apt.getDisplayDate() + " " + apt.getDisplayTime();
                LocalDateTime appointmentTime = LocalDateTime.parse(dateString, formatter);

                if (appointmentTime.minusDays(1).isBefore(now) && appointmentTime.minusHours(23).isAfter(now)) {
                    sendReminders(apt, "Tomorrow", "tomorrow");
                }

                if (appointmentTime.minusHours(1).isBefore(now) && appointmentTime.minusMinutes(45).isAfter(now)) {
                    sendReminders(apt, "In 1 Hour", "in exactly 1 hour");
                }

            } catch (Exception e) {
                // Skip parsing errors
            }
        }
    }

    private void sendReminders(AppointmentEntity apt, String timeTitle, String timeDesc) {
        try {
            UserEntity patient = userService.findById(apt.getUserId());
            String patientEmail = patient.getEmail();

            // Notify Patient
            notificationService.createNotification(
                    patientEmail,
                    "Upcoming Session " + timeTitle,
                    "Your appointment with " + apt.getProviderName() + " is " + timeDesc + " at " + apt.getDisplayTime() + ".",
                    "REMINDER"
            );

            // Notify Doctor
            notificationService.createNotification(
                    apt.getProviderEmail(),
                    "Upcoming Session " + timeTitle,
                    "You have a session with " + apt.getPatientName() + " " + timeDesc + " at " + apt.getDisplayTime() + ".",
                    "REMINDER"
            );

        } catch (Exception e) {
            System.err.println("Failed to send reminder for appointment ID: " + apt.getId() + ". User might no longer exist.");
        }
    }
}