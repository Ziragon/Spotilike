package com.spotilike.shared.exception.resource;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

import java.util.Map;

public class ConcurrentModificationException extends BaseException {

    public ConcurrentModificationException(String resourceName, Long resourceId) {
        super(
                String.format("%s with id=%d was modified by another session. " +
                        "Please refresh and try again.", resourceName, resourceId),
                ErrorType.CONCURRENT_MODIFICATION,
                Map.of(
                        "resourceName", resourceName,
                        "resourceId", resourceId
                )
        );
    }
}