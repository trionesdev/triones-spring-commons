package com.trionesdev.spring.security.web.jwt;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.SecurityToken;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.token.TokenStorage;
import com.trionesdev.spring.security.util.JwtUtils;
import com.trionesdev.spring.security.web.AbstractTokenManager;

public class JwtTokenManager extends AbstractTokenManager {

    public JwtTokenManager(SecurityTokenConfig config, TokenStorage tokenStorage) {
        super(config, tokenStorage);
    }

    @Override
    public SecurityToken createToken(TokenDefinition tokenDefinition) {
        String token = JwtUtils.serialize(tokenDefinition.getSubject(), config.getSecret(), tokenDefinition.getClaims(), config.getExpires());
        return SecurityToken.builder()
                .accessToken(token)
                .build();
    }

    @Override
    public SecurityToken logout(String token) {
        return null;
    }
}
