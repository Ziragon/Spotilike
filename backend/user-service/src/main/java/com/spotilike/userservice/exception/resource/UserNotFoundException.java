package com.spotilike.userservice.exception.resource;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

import java.util.Map;

public class UserNotFoundException extends BaseException {

    public UserNotFoundException(Long userId) {
        super(
                "User not found with id: " + userId,
                ErrorType.USER_NOT_FOUND,
                Map.of("userId", userId)
        );
    }

    public UserNotFoundException(String email) {
        super(
                "User not found with email: " + email,
                ErrorType.USER_NOT_FOUND,
                Map.of("email", email)
        );
    }
}