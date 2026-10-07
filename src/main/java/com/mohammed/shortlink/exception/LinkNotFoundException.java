package com.mohammed.shortlink.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException() {
        super("No link exists for this short code");
    }
}
