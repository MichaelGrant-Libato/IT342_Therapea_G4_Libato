package com.therapea.backend.service;

import com.therapea.backend.entity.AppointmentEntity;
import com.therapea.backend.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    public AppointmentEntity saveAppointment(AppointmentEntity appointment) {
        return appointmentRepository.save(appointment);
    }

    public List<AppointmentEntity> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public List<AppointmentEntity> getAppointmentsByUserId(UUID userId) {
        return appointmentRepository.findByUserId(userId);
    }

    public Optional<AppointmentEntity> getAppointmentById(Long id) {
        return appointmentRepository.findById(id);
    }

    public boolean deleteAppointment(Long id) {
        if (appointmentRepository.existsById(id)) {
            appointmentRepository.deleteById(id);
            return true;
        }
        return false;
    }
}