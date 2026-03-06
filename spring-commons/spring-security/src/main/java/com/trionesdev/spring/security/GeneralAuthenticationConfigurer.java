package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class GeneralAuthenticationConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<GeneralAuthenticationConfigurer<H>, H> {
    private final GeneralAuthenticationFilter generalAuthenticationFilter;

    public GeneralAuthenticationConfigurer(GeneralAuthenticationFilter generalAuthenticationFilter) {
        this.generalAuthenticationFilter = generalAuthenticationFilter;
    }

    @Override
    public void init(H builder) throws Exception {
        super.init(builder);
    }

    @Override
    public void configure(H builder) {
        generalAuthenticationFilter.setAuthenticationManager(builder.getSharedObject(AuthenticationManager.class));
        builder.addFilterAfter(generalAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
