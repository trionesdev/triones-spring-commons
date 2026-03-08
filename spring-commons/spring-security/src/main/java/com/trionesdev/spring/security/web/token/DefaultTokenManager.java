package com.trionesdev.spring.security.web.token;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.TokenType;
import com.trionesdev.spring.security.token.SecurityToken;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.token.TokenStorage;
import com.trionesdev.spring.security.util.JwtUtils;
import com.trionesdev.spring.security.util.TokenUtils;
import com.trionesdev.spring.security.web.AbstractTokenManager;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Objects;
import java.util.UUID;

public class DefaultTokenManager extends AbstractTokenManager {

    public DefaultTokenManager(SecurityTokenConfig config, TokenStorage tokenStorage) {
        super(config, tokenStorage);
    }

    @Override
    public SecurityToken createToken(TokenDefinition tokenDefinition) {
        String token = null;
        String refreshToken = null;
        if (Objects.equals(TokenType.jwt, config.getTokenType())) {
            token = JwtUtils.serialize(tokenDefinition.getSubject(), config.getSecret(), tokenDefinition.getClaims(), config.getExpires());
        } else {
            token = TokenUtils.generateToken(config);
            if (BooleanUtils.isTrue(config.getEnableRefresh())) {
                refreshToken = TokenUtils.generateToken(config);
            }
            tokenStorage.set(token, tokenDefinition.getClaims(), config.getExpires());
            if (BooleanUtils.isTrue(config.getEnableRefresh())) {
                tokenStorage.set(refreshToken, tokenDefinition.getClaims(), config.getRefreshExpires());
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
