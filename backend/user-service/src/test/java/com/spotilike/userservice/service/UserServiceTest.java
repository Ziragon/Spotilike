package com.spotilike.userservice.service;

import com.spotilike.shared.exception.resource.ConcurrentModificationException;
import com.spotilike.userservice.exception.resource.RoleNotFoundException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.model.enums.RoleName;
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
        defaultRole = Role.builder().id(1L).name(RoleName.ROLE_USER).build();
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {
        @Test
        @DisplayName("Successful user create")
        void shouldCreateUser() {
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(defaultRole));
            when(passwordEncoder.encode("password")).thenReturn("hash");

            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.createUser("new@mail.com", "password", "nick");

            assertThat(result.getEmail()).isEqualTo("new@mail.com");
            verify(userRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("ROLE_USER is missing - RoleNotFoundException")
        void shouldThrowWhenDefaultRoleMissing() {
            when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.createUser("a@b.com", "p", "u"))
                    .isInstanceOf(RoleNotFoundException.class);
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
                    .version(1L)
                    .build();
        }

        @Test
        @DisplayName("Updates username and avatarUrl")
        void shouldUpdateBothFields() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(existingUser));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "newName", "newAvatar", 1L);

            assertThat(result.getUsername()).isEqualTo("newName");
            assertThat(result.getAvatarUrl()).isEqualTo("newAvatar");
            verify(userRepository).saveAndFlush(existingUser);
        }

        @Test
        @DisplayName("Updates only username")
        void shouldUpdateOnlyUsername() {
            existingUser.setVersion(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "newNickname", null, 1L);

            assertThat(result.getUsername()).isEqualTo("newNickname");
            assertThat(result.getAvatarUrl()).isEqualTo("oldAvatar");
        }

        @Test
        @DisplayName("Updates only avatarUrl")
        void shouldUpdateOnlyAvatar() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(existingUser));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, null, "newAvatar", 1L);

            assertThat(result.getUsername()).isEqualTo("oldName");
            assertThat(result.getAvatarUrl()).isEqualTo("newAvatar");
            verify(userRepository).saveAndFlush(existingUser);
        }

        @Test
        @DisplayName("Should ignore blank username")
        void shouldIgnoreBlankUsername() {
            existingUser.setVersion(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            User result = userService.updateProfile(1L, "   ", "newAvatar", 1L);

            assertThat(result.getUsername()).isEqualTo("oldName");
            assertThat(result.getAvatarUrl()).isEqualTo("newAvatar");
        }

        @Test
        @DisplayName("Nothing has been given - without save")
        void shouldNotSaveWhenNothingChanged() {
            existingUser.setVersion(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            User result = userService.updateProfile(1L, null, null, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("oldName");
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("User not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.updateProfile(999L, "name", "avatar", 1L))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Throws exception if client version is stale")
        void shouldThrowOnStaleClientVersion() {
            existingUser.setVersion(2L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> userService.updateProfile(1L, "newName", null, 1L))
                    .isInstanceOf(ConcurrentModificationException.class);

            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Returns user")
        void shouldReturnUser() {
            User user = User.builder().id(1L).email("a@b.com").build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(userService.findById(1L)).isEqualTo(user);
        }

        @Test
        @DisplayName("Throws UserNotFoundException")
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
        @DisplayName("Returns the user")
        void shouldReturnUser() {
            User user = User.builder().id(1L).email("a@b.com").build();
            when(userRepository.findByEmail("a@b.com"))
                    .thenReturn(Optional.of(user));

            assertThat(userService.findByEmail("a@b.com")).isEqualTo(user);
        }

        @Test
        @DisplayName("Throws UserNotFoundException")
        void shouldThrowWhenNotFound() {
            when(userRepository.findByEmail("no@b.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByEmail("no@b.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}