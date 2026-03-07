package com.trionesdev.spring.security;

import org.springframework.security.core.Authentication;

public interface AuthenticationInterceptor {
    void before(Authentication authentication);

    void after(Authentication authentication);
}
