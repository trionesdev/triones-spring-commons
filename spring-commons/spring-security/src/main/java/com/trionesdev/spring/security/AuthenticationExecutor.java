package com.trionesdev.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;

public interface AuthenticationExecutor {
    void setAuthenticationManager(AuthenticationManager authenticationManager);

    void setAuthenticationInterceptor(AuthenticationInterceptor authenticationInterceptor);

    void setAuthorityManager(AuthorityManager authorityManager);

    void execute(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain);
}
