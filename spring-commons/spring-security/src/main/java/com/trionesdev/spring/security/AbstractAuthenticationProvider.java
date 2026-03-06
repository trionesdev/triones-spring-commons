package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationProvider;

public abstract class AbstractAuthenticationProvider implements AuthenticationProvider {
    protected final SecurityConfig config;

    public AbstractAuthenticationProvider(SecurityConfig config) {
        this.config = config;
    }
}
