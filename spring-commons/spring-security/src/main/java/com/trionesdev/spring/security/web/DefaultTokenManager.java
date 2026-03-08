package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.SecurityToken;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.token.TokenManager;
import com.trionesdev.spring.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultTokenManager implements TokenManager {
    private final SecurityTokenConfig config;

    @Override
    public SecurityToken createToken(TokenDefinition tokenDefinition) {
        String token = JwtUtils.serialize(tokenDefinition.getSubject(), config.getJwt().getSecret(), tokenDefinition.getClaims(), config.getExpires());
        return SecurityToken.builder()
                .accessToken(token)
                .build();
    }

    @Override
    public SecurityToken logout(String token) {
        return null;
    }
}
