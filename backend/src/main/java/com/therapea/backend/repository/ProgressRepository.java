package com.therapea.backend.repository;

import com.therapea.backend.entity.ProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProgressRepository extends JpaRepository<ProgressEntity, Long> {
    List<ProgressEntity> findByUserId(UUID userId);
}