package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationProvider;

public abstract class AbstractAuthenticationProvider implements AuthenticationProvider {
    protected final SecurityTokenConfig config;

    public AbstractAuthenticationProvider(SecurityTokenConfig config) {
        this.config = config;
    }
}
