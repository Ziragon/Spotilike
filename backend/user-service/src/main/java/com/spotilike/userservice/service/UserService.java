package com.spotilike.userservice.service;

import com.spotilike.shared.exception.resource.ConcurrentModificationException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
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
    public User updateProfile(Long userId, String newUsername, String newAvatarUrl, Long clientVersion) {
        User user = findById(userId);

        // 1. Проверка версии (Optimistic Lock на уровне бизнес-логики)
        if (!Objects.equals(user.getVersion(), clientVersion)) {
            log.warn("Stale data update attempt for user {}. Expected version {}, got {}",
                    userId, user.getVersion(), clientVersion);
            throw new ConcurrentModificationException("User", userId);
        }

        boolean isChanged = false;

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            user.setUsername(newUsername);
            isChanged = true;
        }

        if (newAvatarUrl != null && !newAvatarUrl.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(newAvatarUrl);
            isChanged = true;
        }

        // Не обращаемся к бд если изменений нет
        if (!isChanged) {
            return user;
        }

        try {
            return userRepository.saveAndFlush(user);
        } catch (ObjectOptimisticLockingFailureException _) {
            log.warn("Concurrent update detected for user {}", userId);
            throw new ConcurrentModificationException("User", userId);
        }
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
