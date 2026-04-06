package com.therapea.backend.repository;

import com.therapea.backend.entity.StatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StatRepository extends JpaRepository<StatEntity, Long> {
    List<StatEntity> findByUserId(UUID userId);
}