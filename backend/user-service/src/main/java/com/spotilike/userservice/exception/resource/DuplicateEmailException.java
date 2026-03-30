package com.spotilike.userservice.exception.resource;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

public class DuplicateEmailException extends BaseException {

    public DuplicateEmailException() {
        super(
                "Email already registered",
                ErrorType.DUPLICATE_EMAIL
        );
    }
}