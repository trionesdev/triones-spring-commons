package com.trionesdev.security.demo;

import com.trionesdev.spring.security.GeneralAuthenticationConfigurer;
import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.jwt.JwtAuthenticationFilter;
import com.trionesdev.spring.security.jwt.JwtAuthenticationProvider;
import com.trionesdev.spring.security.jwt.JwtTokenManager;
import com.trionesdev.spring.security.TokenAuthenticationEntryPoint;
import com.trionesdev.spring.security.token.TokenManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        SecurityTokenConfig config = new SecurityTokenConfig();
//        AbstractAuthenticationProcessor processor = new JwtAuthenticationProcessor();
//        processor.setSecurityConfig(config);

        var authFilter = new JwtAuthenticationFilter(config);

        http.authenticationProvider(new JwtAuthenticationProvider(config))
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/login1","/profile").permitAll()
                        .anyRequest().authenticated())
                .with(new GeneralAuthenticationConfigurer<>(authFilter),Customizer.withDefaults())
                .exceptionHandling(e -> e.authenticationEntryPoint(new TokenAuthenticationEntryPoint()))
        ;
        return http.build();
    }

}
