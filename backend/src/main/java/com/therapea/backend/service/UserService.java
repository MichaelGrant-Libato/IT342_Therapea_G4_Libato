package com.therapea.backend.service;

import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserEntity loginUser(LoginDTO dto) {
        RuntimeException authError = new RuntimeException("Invalid email or password.");
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> authError);

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw authError;
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw authError;
        }

        return user;
    }

    public UserEntity saveUser(UserEntity user) {
        return userRepository.save(user);
    }

    public UserEntity findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    public UserEntity getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}