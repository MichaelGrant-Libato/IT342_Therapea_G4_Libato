package com.therapea.backend.features.admin;

import com.therapea.backend.features.users.UserEntity;
import com.therapea.backend.features.users.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            userRepository.findByEmail("admin@therapea.com").ifPresent(user -> {
                userRepository.delete(user);
            });

            // Create a perfect, code-generated Admin
            UserEntity admin = new UserEntity();
            admin.setFullName("System Admin");
            admin.setEmail("admin@therapea.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            admin.setStatus("APPROVED");
            admin.setIsActive(true);
            admin.setEmailVerified(true);
            admin.setProfileCompleted(true);

            userRepository.save(admin);
            System.out.println("✅ Professional Admin Account created with password: admin123");
        };
    }
}