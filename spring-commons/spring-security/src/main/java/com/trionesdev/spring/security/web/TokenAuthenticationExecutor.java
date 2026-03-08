package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AbstractAuthenticationExecutor;
import com.trionesdev.spring.security.SecurityTokenConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;

public class TokenAuthenticationExecutor extends AbstractAuthenticationExecutor {
    public TokenAuthenticationExecutor(SecurityTokenConfig securityTokenConfig) {
        super(securityTokenConfig);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String token = getToken(request);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        TokenAuthenticationToken tokenAuthenticationToken = new TokenAuthenticationToken();
        tokenAuthenticationToken.setToken(token);
        Authentication authentication = this.authenticationManager.authenticate(tokenAuthenticationToken);
        return authentication;
    }

}
