package com.spotilike.userservice.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BaseException {

    public UserNotFoundException(Long identifier) {
        super(
                String.format("User not found with identifier: %s", identifier),
                "USER_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }
}