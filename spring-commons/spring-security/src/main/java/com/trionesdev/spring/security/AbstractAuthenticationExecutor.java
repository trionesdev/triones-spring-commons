package com.trionesdev.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractAuthenticationExecutor implements AuthenticationExecutor {
    protected final SecurityTokenConfig securityTokenConfig;
    protected AuthenticationManager authenticationManager;
    protected AuthenticationInterceptor authenticationInterceptor;
    protected AuthorityManager authorityManager;

    public AbstractAuthenticationExecutor(SecurityTokenConfig securityTokenConfig) {
        this.securityTokenConfig = securityTokenConfig;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public void setAuthProcessor(AuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    public void setAuthorityManager(AuthorityManager authorityManager) {
        this.authorityManager = authorityManager;
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

    public void execute(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        try {
            Authentication authenticationResult = attemptAuthentication(request, response);
            if (authenticationResult != null && authenticationResult.isAuthenticated()) {
                setAuthentication(authenticationResult);
            }
            if (authenticationInterceptor != null) {
                authenticationInterceptor.before(getAuthentication());
            }
            filterChain.doFilter(request, response);
            if (authenticationResult != null && authenticationInterceptor != null) {
                authenticationInterceptor.after(getAuthentication());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
