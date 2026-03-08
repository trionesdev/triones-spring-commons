package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.TokenManager;

public abstract class AbstractTokenManager implements TokenManager {
    protected final SecurityTokenConfig config;

    protected AbstractTokenManager(SecurityTokenConfig config) {
        this.config = config;
    }
}
