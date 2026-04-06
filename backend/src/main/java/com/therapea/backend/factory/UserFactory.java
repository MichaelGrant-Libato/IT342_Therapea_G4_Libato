package com.therapea.backend.factory;

import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserFactory {

    public UserEntity create(UserRegistrationDTO dto, String encodedPassword) {
        UserEntity user = new UserEntity();
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(dto.getRole());

        if ("DOCTOR".equalsIgnoreCase(dto.getRole())) {
            user.setProfileCompleted(false);
        } else {
            user.setProfileCompleted(true);
        }

        return user;
    }
}