package com.trionesdev.spring.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class GeneralAuthenticationConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<GeneralAuthenticationConfigurer<H>, H> {
    private final AbstractAuthenticationFilter authFilter;
    private final AuthProcessor authProcessor;

    public GeneralAuthenticationConfigurer(AbstractAuthenticationFilter authFilter, AuthProcessor authProcessor) {
        this.authFilter = authFilter;
        this.authProcessor = authProcessor;
    }

    @Override
    public void init(H builder) throws Exception {
        super.init(builder);
    }

    @Override
    public void configure(H builder) {
        authFilter.setAuthenticationManager(builder.getSharedObject(AuthenticationManager.class));
        authFilter.setAuthProcessor(authProcessor);
        builder.addFilterAfter(authFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
