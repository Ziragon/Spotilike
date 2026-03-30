package com.spotilike.userservice.exception.resource;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

import java.util.Map;

public class RoleNotFoundException extends BaseException {

    public RoleNotFoundException(String roleName) {
        super(
                "Role not found: " + roleName,
                ErrorType.ROLE_NOT_FOUND,
                Map.of("roleName", roleName)
        );
    }
}