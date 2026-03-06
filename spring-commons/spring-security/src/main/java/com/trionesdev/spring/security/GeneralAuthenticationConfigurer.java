package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class GeneralAuthenticationConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<GeneralAuthenticationConfigurer<H>, H> {
    private final AbstractAuthenticationFilter authFilter;

    public GeneralAuthenticationConfigurer(AbstractAuthenticationFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Override
    public void init(H builder) throws Exception {
        super.init(builder);
    }

    @Override
    public void configure(H builder) {
        authFilter.setAuthenticationManager(builder.getSharedObject(AuthenticationManager.class));
        builder.addFilterAfter(authFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
