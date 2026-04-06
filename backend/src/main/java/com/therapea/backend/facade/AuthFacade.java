package com.therapea.backend.facade;

import com.therapea.backend.dto.UserRegistrationDTO;
import com.therapea.backend.entity.UserEntity;
import com.therapea.backend.factory.UserFactory;
import com.therapea.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthFacade {

    @Autowired
    private UserService userService;

    @Autowired
    private UserFactory userFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void processRegistration(UserRegistrationDTO dto) {
        if (userService.getUserByEmail(dto.getEmail()) != null) {
            throw new RuntimeException("Email is already in use.");
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        UserEntity newUser = userFactory.create(dto, encodedPassword);

        userService.saveUser(newUser);
    }
}