package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.resource.DuplicateEmailException;
import com.spotilike.userservice.exception.resource.RoleNotFoundException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.model.enums.RoleName;
import com.spotilike.userservice.repository.RoleRepository;
import com.spotilike.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(String email,
                           String rawPassword,
                           String username) {

        Role defaultRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> {
                    log.error("Default role ROLE_USER is missing in DB!");
                    return new RoleNotFoundException(RoleName.ROLE_USER);
                });

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .username(username)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .verified(false)
                .build();

        try {
            User saved = userRepository.save(user);
            log.info("User registered: id={}, email={}", saved.getId(), email);
            return saved;
        } catch (DataIntegrityViolationException _) { // Пользователь с таким email уже существует
            log.warn("Duplicate registration attempt: email={}", email);
            throw new DuplicateEmailException();
        }
    }

    @Transactional
    public User updateProfile(Long userId,
                              String newUsername,
                              String newAvatarUrl) {

        User user = findById(userId);
        boolean changed = false;

        if (newUsername != null && !newUsername.isBlank()) {
            user.setUsername(newUsername);
            changed = true;
        }
        if (newAvatarUrl != null) {
            user.setAvatarUrl(newAvatarUrl);
            changed = true;
        }
        if (changed) {
            user = userRepository.save(user);
            log.info("Profile updated: userId={}", userId);
        }
        return user;
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: id={}", id);
                    return new UserNotFoundException(id);
                });
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found: email={}", email);
                    return new UserNotFoundException(
                            "User not found with email: " + email);
                });
    }
}
