package com.therapea.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "dashboard_stats")
public class StatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId; // Matches UserEntity ID

    private String iconKey;
    private String color;
    private String statValue;
    private String label;
    private String changeStr;
    private boolean positive;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getIconKey() { return iconKey; }
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getStatValue() { return statValue; }
    public void setStatValue(String statValue) { this.statValue = statValue; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getChangeStr() { return changeStr; }
    public void setChangeStr(String changeStr) { this.changeStr = changeStr; }
    public boolean isPositive() { return positive; }
    public void setPositive(boolean positive) { this.positive = positive; }
}