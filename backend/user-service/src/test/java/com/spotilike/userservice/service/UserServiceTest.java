package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.conflict.DuplicateEmailException;
import com.spotilike.userservice.exception.notfound.RoleNotFoundException;
import com.spotilike.userservice.exception.notfound.UserNotFoundException;
import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.repository.RoleRepository;
import com.spotilike.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Role defaultRole;

    @BeforeEach
    void setUp() {
        defaultRole = Role.builder().id(1L).name("USER").build();
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("Успешное создание пользователя")
        void shouldCreateUser() {

            when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
            when(roleRepository.findByName("USER"))
                    .thenReturn(Optional.of(defaultRole));
            when(passwordEncoder.encode("rawPass")).thenReturn("hashedPass");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            User result = userService.createUser("new@mail.com", "rawPass", "nick");

            assertThat(result.getEmail()).isEqualTo("new@mail.com");
            assertThat(result.getPasswordHash()).isEqualTo("hashedPass");
            assertThat(result.getUsername()).isEqualTo("nick");
            assertThat(result.getRoles()).containsExactly(defaultRole);
            assertThat(result.isVerified()).isFalse();

            verify(passwordEncoder).encode("rawPass");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Дубликат email — DuplicateEmailException")
        void shouldThrowOnDuplicateEmail() {

            when(userRepository.existsByEmail("dup@mail.com")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.createUser("dup@mail.com", "pass", "nick"))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("Роль USER отсутствует — RoleNotFoundException")
        void shouldThrowWhenDefaultRoleMissing() {

            when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
            when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.createUser("new@mail.com", "pass", "nick"))
                    .isInstanceOf(RoleNotFoundException.class);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        private User existingUser;

        @BeforeEach
        void setUp() {
            existingUser = User.builder()
                    .id(1L)
                    .username("oldName")
                    .avatarUrl("oldAvatar")
                    .build();
        }

        private void stubUserFound() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(existingUser));
        }

        @Test
        @DisplayName("Обновляет username и avatarUrl")
        void shouldUpdateBothFields() {
            stubUserFound();
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "newName", "newAvatar");

            assertThat(result.getUsername()).isEqualTo("newName");
            assertThat(result.getAvatarUrl()).isEqualTo("newAvatar");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("Обновляет только username, avatarUrl = null")
        void shouldUpdateOnlyUsername() {
            stubUserFound();
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "newName", null);

            assertThat(result.getUsername()).isEqualTo("newName");
            assertThat(result.getAvatarUrl()).isEqualTo("oldAvatar");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("Обновляет только avatarUrl, username = null")
        void shouldUpdateOnlyAvatar() {
            stubUserFound();
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, null, "newAvatar");

            assertThat(result.getUsername()).isEqualTo("oldName");
            assertThat(result.getAvatarUrl()).isEqualTo("newAvatar");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("Пустой username игнорируется")
        void shouldIgnoreBlankUsername() {
            stubUserFound();
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "   ", "newAvatar");

            assertThat(result.getUsername()).isEqualTo("oldName");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("Ничего не передано — save не вызывается")
        void shouldNotSaveWhenNothingChanged() {
            stubUserFound();

            User result = userService.updateProfile(1L, null, null);

            assertThat(result.getUsername()).isEqualTo("oldName");
            assertThat(result.getAvatarUrl()).isEqualTo("oldAvatar");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Пользователь не найден — UserNotFoundException")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.updateProfile(999L, "name", "avatar"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Возвращает пользователя")
        void shouldReturnUser() {
            User user = User.builder().id(1L).email("a@b.com").build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(userService.findById(1L)).isEqualTo(user);
        }

        @Test
        @DisplayName("Бросает UserNotFoundException")
        void shouldThrowWhenNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findById(1L))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("Возвращает пользователя")
        void shouldReturnUser() {
            User user = User.builder().id(1L).email("a@b.com").build();
            when(userRepository.findByEmail("a@b.com"))
                    .thenReturn(Optional.of(user));

            assertThat(userService.findByEmail("a@b.com")).isEqualTo(user);
        }

        @Test
        @DisplayName("Бросает UserNotFoundException")
        void shouldThrowWhenNotFound() {
            when(userRepository.findByEmail("no@b.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByEmail("no@b.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}