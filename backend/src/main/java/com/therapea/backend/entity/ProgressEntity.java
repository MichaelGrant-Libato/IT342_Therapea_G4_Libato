package com.therapea.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "wellness_progress")
public class ProgressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    private String label;
    private int progressValue;
    private String cssClass;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getProgressValue() { return progressValue; }
    public void setProgressValue(int progressValue) { this.progressValue = progressValue; }
    public String getCssClass() { return cssClass; }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }
}