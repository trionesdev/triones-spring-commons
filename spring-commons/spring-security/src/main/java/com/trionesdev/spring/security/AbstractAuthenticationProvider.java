package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationProvider;

public abstract class AbstractAuthenticationProvider implements AuthenticationProvider {
    protected final SecurityTokenConfig config;
    protected final AuthorityManager authorityManager;

    public AbstractAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager) {
        this.config = config;
        this.authorityManager = authorityManager;
    }
}
