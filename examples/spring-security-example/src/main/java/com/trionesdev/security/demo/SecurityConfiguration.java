package com.trionesdev.security.demo;

import com.trionesdev.spring.security.*;
import com.trionesdev.spring.security.jwt.JwtAuthenticationFilter;
import com.trionesdev.spring.security.jwt.JwtAuthenticationProvider;
import com.trionesdev.spring.security.jwt.JwtTokenManager;
import com.trionesdev.spring.security.token.TokenManager;
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
        return new JwtTokenManager(config);
    }

    @Bean
    public AuthProcessor authenticationProcessor() {
        return new CustomAuthProcessor();
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProcessor processor, AuthorityManager authorityManager) throws Exception {

        SecurityTokenConfig config = new SecurityTokenConfig();
        var authFilter = new JwtAuthenticationFilter(config);

        http.authenticationProvider(new JwtAuthenticationProvider(config, authorityManager))
                .csrf(AbstractHttpConfigurer::disable)
//                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/login1", "/profile").permitAll()
                        .anyRequest().authenticated())
                .with(new GeneralAuthenticationConfigurer<>(authFilter, processor), Customizer.withDefaults())
                .exceptionHandling(e ->
                        e.authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                                .accessDeniedHandler(new JsonAccessDeniedHandler()))
        ;
        return http.build();
    }

}
