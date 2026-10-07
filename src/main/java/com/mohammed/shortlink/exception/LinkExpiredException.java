package com.mohammed.shortlink.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException() {
        super("This short link has expired");
    }
}
