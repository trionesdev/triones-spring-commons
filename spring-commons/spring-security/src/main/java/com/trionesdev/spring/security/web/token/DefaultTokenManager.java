package com.trionesdev.spring.security.web.token;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.TokenType;
import com.trionesdev.spring.security.token.SecurityToken;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.util.JwtUtils;
import com.trionesdev.spring.security.web.AbstractTokenManager;

import java.util.Objects;
import java.util.UUID;

public class DefaultTokenManager extends AbstractTokenManager {

    public DefaultTokenManager(SecurityTokenConfig config) {
        super(config);
    }

    @Override
    public SecurityToken createToken(TokenDefinition tokenDefinition) {
        String token = null;
        String refreshToken = null;
        if (Objects.equals(TokenType.jwt, config.getTokenType())) {
            token = JwtUtils.serialize(tokenDefinition.getSubject(), config.getSecret(), tokenDefinition.getClaims(), config.getExpires());
        } else {
            switch (config.getTokenStyle()) {
                case uuid -> {
                    token = UUID.randomUUID().toString();
                    refreshToken = UUID.randomUUID().toString();
                }
                case simpleUuid -> {
                    token = UUID.randomUUID().toString().replaceAll("-", "");
                    refreshToken = UUID.randomUUID().toString().replaceAll("-", "");
                }
            }
        }
        return SecurityToken.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public SecurityToken logout(String token) {
        return null;
    }
}
