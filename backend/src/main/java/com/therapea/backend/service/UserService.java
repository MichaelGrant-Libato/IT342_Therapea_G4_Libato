package com.therapea.backend.service;

import com.therapea.backend.dto.LoginDTO;
import com.therapea.backend.dto.UserRegistrationDTO;
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

    public UserEntity registerUser(UserRegistrationDTO dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Email is already in use.");
        }

        // 👇 ADD THESE THREE LINES 👇
        System.out.println("====== REGISTRATION RECEIVED ======");
        System.out.println("👉 Raw Password from React: [" + dto.getPassword() + "]");
        System.out.println("👉 Generated Hash: [" + passwordEncoder.encode(dto.getPassword()) + "]");

        UserEntity user = new UserEntity();
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        return userRepository.save(user);
    }

    public UserEntity loginUser(LoginDTO dto) {
        // 1. Define a single, generic error message
        RuntimeException authError = new RuntimeException("Invalid email or password.");

        // 2. Throw the generic error if the email is not found
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> authError);

        // 3. Throw the SAME generic error if they are a Google OAuth user
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw authError;
        }

        System.out.println("👉 Password typed in React: [" + dto.getPassword() + "]");
        System.out.println("👉 Hash saved in Database: [" + user.getPassword() + "]");

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

    /**
     * Get user by email without throwing exception - returns null if not found
     */
    public UserEntity getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}