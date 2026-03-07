package com.trionesdev.security.demo;

import com.trionesdev.spring.security.AuthenticationInterceptor;
import org.springframework.security.core.Authentication;

public class CustomAuthenticationInterceptor implements AuthenticationInterceptor {
    @Override
    public void before(Authentication authentication) {
        System.out.println("before");
    }

    @Override
    public void after(Authentication authentication) {
        System.out.println("after");

    }
}
