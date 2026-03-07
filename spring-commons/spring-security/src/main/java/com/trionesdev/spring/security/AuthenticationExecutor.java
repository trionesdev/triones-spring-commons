package com.trionesdev.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthenticationExecutor {
    void execute(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain);
}
