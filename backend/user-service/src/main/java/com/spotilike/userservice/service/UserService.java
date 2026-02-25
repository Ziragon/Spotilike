package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.model.User;
import com.spotilike.userservice.repository.RoleRepository;
import com.spotilike.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public User createUser(User user) {
        return userRepository.save(user);
    }

    private Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
