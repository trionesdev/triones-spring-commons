package com.trionesdev.spring.security.jwt;

import com.trionesdev.spring.security.AbstractAuthenticationFilter;
import com.trionesdev.spring.security.SecurityTokenConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

public class JwtAuthenticationFilter extends AbstractAuthenticationFilter {
    public JwtAuthenticationFilter(SecurityTokenConfig securityTokenConfig) {
        super(securityTokenConfig);
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(AUTHORIZATION);
        if (StringUtils.isNotBlank(token)) {
            token = token.replace("Bearer", "").trim();
        }
        if (StringUtils.isBlank(token)) {
            if (isWebSocketRequest(request)) {
                token = request.getParameter(securityTokenConfig.getTokenKey());
            }
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(authorities);
        jwtAuthenticationToken.setToken(token);
        Authentication authentication = this.authenticationManager.authenticate(jwtAuthenticationToken);
//        if (this.authorityManager != null){
//            authorities = this.authorityManager.getAuthorities(authentication);
//
//        }
        setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
