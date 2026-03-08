package com.trionesdev.security.demo;

import com.trionesdev.spring.security.*;
import com.trionesdev.spring.security.token.TokenStorage;
import com.trionesdev.spring.security.web.TokenAuthenticationExecutor;
import com.trionesdev.spring.security.web.token.TokenAuthenticationProvider;
import com.trionesdev.spring.security.web.token.DefaultTokenManager;
import com.trionesdev.spring.security.token.TokenManager;
import com.trionesdev.spring.security.web.GeneralAuthenticationConfigurer;
import com.trionesdev.spring.security.web.TokenAccessDeniedHandler;
import com.trionesdev.spring.security.web.TokenAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public TokenManager tokenManager() {
        SecurityTokenConfig config = new SecurityTokenConfig();
        return new DefaultTokenManager(config);
    }

    @Bean
    public AuthenticationInterceptor authenticationProcessor() {
        return new CustomAuthenticationInterceptor();
    }

    @Bean
    public AuthorityManager authorityManager() {
        return new AuthorityManager() {
            @Override
            public List<String> getRoles(Authentication authentication) {
                return List.of();
            }

            @Override
            public List<String> getPermissions(Authentication authentication) {
                return List.of("say");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationInterceptor processor, AuthorityManager authorityManager,
                                                   ObjectProvider<TokenStorage> tokenStorage
    ) throws Exception {

        SecurityTokenConfig config = new SecurityTokenConfig();
        var authExecutor = new TokenAuthenticationExecutor(config);

        GeneralAuthenticationConfigurer<HttpSecurity> authConfigurer = new GeneralAuthenticationConfigurer<>(authExecutor);
        authConfigurer.setAuthenticationInterceptor(processor);

        http.authenticationProvider(new TokenAuthenticationProvider(config, authorityManager, tokenStorage.getIfAvailable()))
                .csrf(AbstractHttpConfigurer::disable)
//                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/login", "/profile").permitAll()
                        .anyRequest().authenticated())
                .with(authConfigurer, Customizer.withDefaults())
                .exceptionHandling(e ->
                        e.authenticationEntryPoint(new TokenAuthenticationEntryPoint())
                                .accessDeniedHandler(new TokenAccessDeniedHandler()))
        ;
        return http.build();
    }

}
