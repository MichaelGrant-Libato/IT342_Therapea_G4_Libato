// UserService.java
package com.therapea.backend.features.users;

import com.therapea.backend.features.auth.LoginDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password."));

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("Please log in using Google.");
        }

        System.out.println("👉 Password typed in React: [" + dto.getPassword() + "]");
        System.out.println("👉 Hash saved in Database: [" + user.getPassword() + "]");

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password.");
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

    public void changePassword(String email, String oldPassword, String newPassword) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Incorrect current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public UserEntity findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
    }

    public UserEntity updateUserProfile(String email, UserRegistrationDTO dto) {
        UserEntity user = findByEmail(email);

        if (dto.getFullName() != null) user.setFullName(dto.getFullName());
        if (dto.getAvailableSchedule() != null) user.setAvailableSchedule(dto.getAvailableSchedule());
        if (dto.getWhatToExpect() != null) user.setWhatToExpect(dto.getWhatToExpect());

        if (dto.getProfileCompleted() != null) user.setProfileCompleted(dto.getProfileCompleted());

        if (dto.getProfilePictureUrl() != null) user.setProfilePictureUrl(dto.getProfilePictureUrl());

        return userRepository.save(user);
    }

}