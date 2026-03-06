package com.trionesdev.security.demo;

import com.trionesdev.spring.security.AbstractAuthenticationProcessor;
import com.trionesdev.spring.security.GeneralAuthenticationConfigurer;
import com.trionesdev.spring.security.GeneralAuthenticationFilter;
import com.trionesdev.spring.security.SecurityConfig;
import com.trionesdev.spring.security.jwt.JwtAuthenticationProcessor;
import com.trionesdev.spring.security.jwt.JwtAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        SecurityConfig config = new SecurityConfig();
        AbstractAuthenticationProcessor processor = new JwtAuthenticationProcessor();
        processor.setSecurityConfig(config);

        http.authenticationProvider(new JwtAuthenticationProvider(config))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .with(new GeneralAuthenticationConfigurer<>(new GeneralAuthenticationFilter(processor)), Customizer.withDefaults())
        ;
        return http.build();
    }

}
