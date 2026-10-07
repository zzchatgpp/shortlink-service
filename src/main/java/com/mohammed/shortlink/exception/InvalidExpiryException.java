package com.mohammed.shortlink.exception;

public class InvalidExpiryException extends RuntimeException {
    public InvalidExpiryException() {
        this("expiresAt must be in the future");
    }

    public InvalidExpiryException(String message) {
        super(message);
    }
}
