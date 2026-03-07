package com.trionesdev.spring.security.jwt;

import com.trionesdev.spring.security.AbstractAuthenticationExecutor;
import com.trionesdev.spring.security.SecurityTokenConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;

public class JwtAuthenticationExecutor extends AbstractAuthenticationExecutor {
    public JwtAuthenticationExecutor(SecurityTokenConfig securityTokenConfig) {
        super(securityTokenConfig);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String token = getToken(request);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken();
        jwtAuthenticationToken.setToken(token);
        Authentication authentication = this.authenticationManager.authenticate(jwtAuthenticationToken);
        return authentication;
    }

}
