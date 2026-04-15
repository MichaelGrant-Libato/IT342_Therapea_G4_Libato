package com.therapea.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Must be Long to match BIGINT

    private String email;
    private String assessmentType;
    private Integer clinicalScore;
    private String riskLevel;
    private String status;

    @CreationTimestamp
    private LocalDateTime createdAt;
}