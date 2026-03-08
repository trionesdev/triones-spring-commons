package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AuthenticationExecutor;
import com.trionesdev.spring.security.AuthenticationInterceptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 通用认证配置器
 * @param <H>
 */
public class GeneralAuthenticationConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<GeneralAuthenticationConfigurer<H>, H> {
    private final AuthenticationExecutor authExecutor;
    private AuthenticationInterceptor authenticationInterceptor;

    public GeneralAuthenticationConfigurer(AuthenticationExecutor authExecutor) {
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
        authExecutor.setAuthenticationInterceptor(authenticationInterceptor);
        builder.addFilterAfter(new GeneralAuthenticationFilter(authExecutor), UsernamePasswordAuthenticationFilter.class);
    }
}
