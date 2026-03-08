package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.SecurityToken;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.util.JwtUtils;

public class DefaultTokenManager extends AbstractTokenManager {

    public DefaultTokenManager(SecurityTokenConfig config) {
        super(config);
    }

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
