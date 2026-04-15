package com.therapea.backend.repository;

import com.therapea.backend.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    // Custom query to fetch a user's assessments, newest first
    List<Assessment> findByEmailOrderByCreatedAtDesc(String email);
}