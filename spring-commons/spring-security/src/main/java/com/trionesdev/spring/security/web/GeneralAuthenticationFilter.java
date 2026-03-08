package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AbstractAuthenticationExecutor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class GeneralAuthenticationFilter extends OncePerRequestFilter {
    private final AbstractAuthenticationExecutor authenticationExecutor;

    public GeneralAuthenticationFilter(AbstractAuthenticationExecutor authenticationExecutor) {
        this.authenticationExecutor = authenticationExecutor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        authenticationExecutor.execute(request, response, filterChain);
    }
}
