package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AuthenticationExecutor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 通用token认证过滤器
 */
public class GeneralAuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticationExecutor authenticationExecutor;

    public GeneralAuthenticationFilter(AuthenticationExecutor authenticationExecutor) {
        this.authenticationExecutor = authenticationExecutor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        authenticationExecutor.execute(request, response, filterChain);
    }
}
