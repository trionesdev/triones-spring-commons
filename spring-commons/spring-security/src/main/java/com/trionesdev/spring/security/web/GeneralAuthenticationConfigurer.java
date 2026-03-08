package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AbstractAuthenticationExecutor;
import com.trionesdev.spring.security.AuthenticationInterceptor;
import com.trionesdev.spring.security.web.GeneralAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class GeneralAuthenticationConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<GeneralAuthenticationConfigurer<H>, H> {
    private final AbstractAuthenticationExecutor authExecutor;
    private AuthenticationInterceptor authenticationInterceptor;

    public GeneralAuthenticationConfigurer(AbstractAuthenticationExecutor authExecutor) {
        this.authExecutor = authExecutor;
    }

    public void setAuthenticationInterceptor(AuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void init(H builder) throws Exception {
        super.init(builder);
    }

    @Override
    public void configure(H builder) {
        authExecutor.setAuthenticationManager(builder.getSharedObject(AuthenticationManager.class));
        authExecutor.setAuthProcessor(authenticationInterceptor);
        builder.addFilterAfter(new GeneralAuthenticationFilter(authExecutor), UsernamePasswordAuthenticationFilter.class);
    }
}
