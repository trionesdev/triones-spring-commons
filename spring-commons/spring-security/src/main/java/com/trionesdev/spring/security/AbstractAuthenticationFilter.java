package com.trionesdev.spring.security;

import com.trionesdev.spring.security.jwt.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.log.LogMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAuthenticationFilter extends OncePerRequestFilter {
    protected final SecurityTokenConfig securityTokenConfig;
    protected AuthenticationManager authenticationManager;
    protected AuthProcessor authProcessor;
    protected AuthorityManager authorityManager;

    public AbstractAuthenticationFilter(SecurityTokenConfig securityTokenConfig) {
        this.securityTokenConfig = securityTokenConfig;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public void setAuthProcessor(AuthProcessor authProcessor) {
        this.authProcessor = authProcessor;
    }

    protected boolean isWebSocketRequest(HttpServletRequest request) {
        String upgrade = request.getHeader("Upgrade");
        String connection = request.getHeader("Connection");

        return "websocket".equalsIgnoreCase(upgrade) &&
                connection != null &&
                connection.toLowerCase().contains("upgrade");
    }


    public void setAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    public Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication;
    }

    public abstract Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authenticationResult = attemptAuthentication(request, response);
        if (authenticationResult != null && authenticationResult.isAuthenticated()) {
            setAuthentication(authenticationResult);
        }
        if (authProcessor != null) {
            authProcessor.before(getAuthentication());
        }
        filterChain.doFilter(request, response);
        if (authenticationResult != null && authProcessor != null) {
            authProcessor.after(getAuthentication());
        }
    }
}
