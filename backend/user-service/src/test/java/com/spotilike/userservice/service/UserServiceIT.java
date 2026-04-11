package com.spotilike.userservice.service;

import com.spotilike.userservice.BaseIT;
import com.spotilike.userservice.exception.resource.DuplicateEmailException;
import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.model.enums.RoleName;
import com.spotilike.userservice.repository.RoleRepository;
import com.spotilike.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceIT extends BaseIT {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(RoleName.ROLE_USER).build()));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("The user is saved correctly in the database")
    @Transactional
    void shouldPersistUserWithRoleAndHashedPassword() {
        User user = userService.createUser("test@mail.com", "rawPass", "nick");

        User fromDb = userRepository.findById(user.getId()).orElseThrow();

        assertThat(fromDb.getEmail()).isEqualTo("test@mail.com");
        assertThat(fromDb.getPasswordHash()).isNotEqualTo("rawPass");
        assertThat(fromDb.getRoles()).extracting(Role::getName)
                .containsExactly(RoleName.ROLE_USER);
    }

    @Test
    @DisplayName("Duplicate email")
    void shouldRejectDuplicateEmailInDatabase() {
        userService.createUser("dup@mail.com", "pass1", "nick1");

        assertThatThrownBy(() ->
                userService.createUser("dup@mail.com", "pass2", "nick2"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("updateProfile saves changes to database")
    @Transactional
    void shouldPersistProfileChanges() {
        User user = userService.createUser("u@mail.com", "pass", "oldName");

        userService.updateProfile(user.getId(), "newName", "newAvatar", 0L);

        User fromDb = userRepository.findById(user.getId()).orElseThrow();
        assertThat(fromDb.getUsername()).isEqualTo("newName");
        assertThat(fromDb.getAvatarUrl()).isEqualTo("newAvatar");
    }
}
