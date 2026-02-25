package com.spotilike.userservice.exception.conflict;

import com.spotilike.userservice.exception.base.ErrorType;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException(String email) {
        super("User", "email", maskEmail(email), ErrorType.DUPLICATE_EMAIL);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}