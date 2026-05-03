package com.therapea.backend.repository;

import com.therapea.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    // Finds a user by their email address
    Optional<UserEntity> findByEmail(String email);

    // Finds all users that match a specific role (e.g., "DOCTOR")
    List<UserEntity> findByRole(String role);
}