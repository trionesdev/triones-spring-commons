package com.trionesdev.spring.security.jwt;

import com.trionesdev.spring.security.AbstractAuthenticationFilter;
import com.trionesdev.spring.security.SecurityTokenConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

public class JwtAuthenticationFilter extends AbstractAuthenticationFilter {
    public JwtAuthenticationFilter(SecurityTokenConfig securityTokenConfig) {
        super(securityTokenConfig);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader(AUTHORIZATION);
        if (StringUtils.isNotBlank(token)) {
            token = token.replace("Bearer", "").trim();
        }
        if (StringUtils.isBlank(token)) {
            if (isWebSocketRequest(request)) {
                token = request.getParameter(securityTokenConfig.getTokenKey());
            }
        }
        if (StringUtils.isBlank(token)) {
            return null;
        }
        JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken();
        jwtAuthenticationToken.setToken(token);
        Authentication authentication = this.authenticationManager.authenticate(jwtAuthenticationToken);
        return authentication;
    }

}
