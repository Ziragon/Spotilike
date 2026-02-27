package com.spotilike.userservice.exception.notfound;

import com.spotilike.userservice.exception.base.ErrorType;

public class RoleNotFoundException extends NotFoundException {

    public RoleNotFoundException(String name) {
        super("Role", name, ErrorType.ROLE_NOT_FOUND);
    }
}