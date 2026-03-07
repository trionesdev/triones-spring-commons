package com.trionesdev.spring.security;

import org.springframework.security.core.Authentication;

public interface AuthProcessor {
    void before(Authentication authentication);

    void after(Authentication authentication);
}
