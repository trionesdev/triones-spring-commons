package com.trionesdev.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public abstract class AbstractAuthenticationFilter extends OncePerRequestFilter {
    protected final SecurityTokenConfig securityTokenConfig;
    protected AuthenticationManager authenticationManager;

    public AbstractAuthenticationFilter(SecurityTokenConfig securityTokenConfig) {
        this.securityTokenConfig = securityTokenConfig;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
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
        return SecurityContextHolder.getContext().getAuthentication();
    }

}
