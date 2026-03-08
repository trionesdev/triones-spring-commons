package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.TokenManager;
import com.trionesdev.spring.security.token.TokenStorage;

public abstract class AbstractTokenManager implements TokenManager {
    protected final SecurityTokenConfig config;
    protected final TokenStorage tokenStorage;

    protected AbstractTokenManager(SecurityTokenConfig config, TokenStorage tokenStorage) {
        this.config = config;
        this.tokenStorage = tokenStorage;
    }
}
