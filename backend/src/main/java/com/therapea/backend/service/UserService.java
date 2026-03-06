package com.therapea.backend.service;

import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public UserEntity registerUser(UserRegistrationDTO dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Email is already in use.");
        }
        UserEntity user = new UserEntity();
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setRole(dto.getRole());
        return userRepository.save(user);
    }

    public UserEntity loginUser(LoginDTO dto) {
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));
        if (!user.getPassword().equals(dto.getPassword())) {
            throw new RuntimeException("Invalid password.");
        }
        return user;
    }
}