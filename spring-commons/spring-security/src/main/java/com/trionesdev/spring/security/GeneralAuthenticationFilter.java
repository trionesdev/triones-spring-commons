package com.trionesdev.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class GeneralAuthenticationFilter extends OncePerRequestFilter {
    private final AbstractAuthenticationProcessor processor;
    private AuthenticationManager authenticationManager;

    public GeneralAuthenticationFilter(AbstractAuthenticationProcessor processor) {
        this.processor = processor;

    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (processor != null) {
            processor.setAuthenticationManager(authenticationManager);
            processor.process(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
