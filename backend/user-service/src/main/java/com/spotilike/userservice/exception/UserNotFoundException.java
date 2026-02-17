package com.spotilike.userservice.exception;

import java.util.Map;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long userId) {
        super(
                "User not found with id: " + userId,
                ErrorType.USER_NOT_FOUND,
                Map.of("userId", userId)
        );
    }
}