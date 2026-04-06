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
    private String providerOrPatientName;
    private String appointmentType;
    private boolean isToday;

    @ElementCollection
    @CollectionTable(name = "appointment_tags", joinColumns = @JoinColumn(name = "appointment_id"))
    private List<TagEmbeddable> tags;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getDisplayDate() { return displayDate; }
    public void setDisplayDate(String displayDate) { this.displayDate = displayDate; }
    public String getDisplayTime() { return displayTime; }
    public void setDisplayTime(String displayTime) { this.displayTime = displayTime; }
    public String getProviderOrPatientName() { return providerOrPatientName; }
    public void setProviderOrPatientName(String providerOrPatientName) { this.providerOrPatientName = providerOrPatientName; }
    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }
    public boolean isToday() { return isToday; }
    public void setToday(boolean today) { isToday = today; }
    public List<TagEmbeddable> getTags() { return tags; }
    public void setTags(List<TagEmbeddable> tags) { this.tags = tags; }
}