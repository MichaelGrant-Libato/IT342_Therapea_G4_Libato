package com.therapea.backend.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class AppointmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    private String displayDate;
    private String displayTime;
    private String appointmentType;
    private boolean isToday;

    @Column
    private String providerId;

    @Column
    private String providerName;

    // 🔴 ADDED THIS FIELD: Required for the messaging system to work
    @Column(name = "provider_email")
    private String providerEmail;

    @Column
    private String patientName;

    @Column
    private String status;

    @Column
    private String cancelReason;

    private String assessmentType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Added to catch the payload from Checkout.tsx
    @Column
    private Integer amountPaid;

    @ElementCollection
    @CollectionTable(name = "appointment_tags", joinColumns = @JoinColumn(name = "appointment_id"))
    private List<TagEmbeddable> tags;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getDisplayDate() { return displayDate; }
    public void setDisplayDate(String displayDate) { this.displayDate = displayDate; }

    public String getDisplayTime() { return displayTime; }
    public void setDisplayTime(String displayTime) { this.displayTime = displayTime; }

    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }

    public boolean isToday() { return isToday; }
    public void setToday(boolean today) { isToday = today; }

    public List<TagEmbeddable> getTags() { return tags; }
    public void setTags(List<TagEmbeddable> tags) { this.tags = tags; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    // 🔴 ADDED THIS GETTER AND SETTER
    public String getProviderEmail() { return providerEmail; }
    public void setProviderEmail(String providerEmail) { this.providerEmail = providerEmail; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    // FIXED: Added getters and setters for assessmentType
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getAmountPaid() { return amountPaid; }
    public void setAmountPaid(Integer amountPaid) { this.amountPaid = amountPaid; }
}