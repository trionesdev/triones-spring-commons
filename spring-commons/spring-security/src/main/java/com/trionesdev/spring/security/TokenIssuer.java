package com.trionesdev.spring.security;

import com.trionesdev.commons.core.jwt.JwtClaims;
import com.trionesdev.commons.model.AuthToken;

public interface TokenIssuer {
    AuthToken issue(String subject, JwtClaims claims);

    AuthToken refresh(String refreshTokenValue);
}
