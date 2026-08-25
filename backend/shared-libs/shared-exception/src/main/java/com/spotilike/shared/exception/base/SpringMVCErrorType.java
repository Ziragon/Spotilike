package com.spotilike.shared.exception.base;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Custom Spring MVC type errors, not a business logic
@Getter
@RequiredArgsConstructor
public enum SpringMVCErrorType {

    MALFORMED_JSON("MALFORMED_REQUEST"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE"),
    MISSING_PARAMETER("MISSING_PARAMETER"),
    ENDPOINT_NOT_FOUND("RESOURCE_NOT_FOUND"),
    TYPE_MISMATCH("TYPE_MISMATCH"),
    BAD_REQUEST("BAD_REQUEST");

    private final String code;
}
