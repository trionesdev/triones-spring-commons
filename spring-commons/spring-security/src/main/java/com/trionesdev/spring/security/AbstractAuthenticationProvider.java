package com.trionesdev.spring.security;

import com.trionesdev.spring.security.token.TokenStorage;
import org.springframework.security.authentication.AuthenticationProvider;

public abstract class AbstractAuthenticationProvider implements AuthenticationProvider {
    protected final SecurityTokenConfig config;
    protected final AuthorityManager authorityManager;
    protected final TokenStorage tokenStorage;

    public AbstractAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager, TokenStorage tokenStorage) {
        this.config = config;
        this.authorityManager = authorityManager;
        this.tokenStorage = tokenStorage;
    }
}
