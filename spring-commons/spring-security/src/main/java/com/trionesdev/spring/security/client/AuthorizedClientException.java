package com.trionesdev.spring.security.client;

public class AuthorizedClientException extends RuntimeException {
    public AuthorizedClientException(String message) {
        super(message);
    }

    public AuthorizedClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
