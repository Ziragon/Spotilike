package com.spotilike.userservice.service;

import com.spotilike.userservice.BaseIT;
import com.spotilike.userservice.exception.conflict.DuplicateEmailException;
import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.repository.RoleRepository;
import com.spotilike.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserServiceIT extends BaseIT {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        roleRepository.save(Role.builder().name("USER").build());
    }

    @Test
    @DisplayName("Пользователь сохраняется в БД с ролью и хешированным паролем")
    void shouldPersistUserWithRoleAndHashedPassword() {
        User user = userService.createUser("test@mail.com", "rawPass", "nick");

        User fromDb = userRepository.findById(user.getId()).orElseThrow();

        assertThat(fromDb.getEmail()).isEqualTo("test@mail.com");
        assertThat(fromDb.getPasswordHash()).isNotEqualTo("rawPass");
        assertThat(fromDb.getRoles()).extracting(Role::getName)
                .containsExactly("USER");
    }

    @Test
    @DisplayName("Дубликат email — constraint работает на уровне БД")
    void shouldRejectDuplicateEmailInDatabase() {
        userService.createUser("dup@mail.com", "pass1", "nick1");

        assertThatThrownBy(() ->
                userService.createUser("dup@mail.com", "pass2", "nick2"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("updateProfile сохраняет изменения в БД")
    void shouldPersistProfileChanges() {
        User user = userService.createUser("u@mail.com", "pass", "oldName");

        userService.updateProfile(user.getId(), "newName", "newAvatar");

        User fromDb = userRepository.findById(user.getId()).orElseThrow();
        assertThat(fromDb.getUsername()).isEqualTo("newName");
        assertThat(fromDb.getAvatarUrl()).isEqualTo("newAvatar");
    }
}