package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实的 springSecurityFilterChain 验证 {@link TokenEndpointConfigurer}：CSRF 开启时仍可访问、
 * 无需 permitAll、Bean 自动获取。
 */
class TokenEndpointConfigurerTest {
    private AnnotationConfigWebApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    private Filter chain(Class<?> config) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(config);
        context.refresh();
        return context.getBean("springSecurityFilterChain", Filter.class);
    }

    private static MockHttpServletResponse post(Filter chain, String uri, String secret) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.addParameter("grant_type", "client_credentials");
        request.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(("svc:" + secret).getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        chain.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void defaultsResolveBeansAndBypassCsrfAndAuthorization() throws Exception {
        Filter chain = chain(DefaultsConfig.class);
        MockHttpServletResponse ok = post(chain, TokenEndpointConfigurer.DEFAULT_PATH, "pw");
        assertEquals(200, ok.getStatus(), ok.getContentAsString());
        String token = (String) JSONObjectUtils.parse(ok.getContentAsString()).get("access_token");
        assertEquals("svc", context.getBean(JwtFacade.class).parse(token).getSubject());
        // 使用容器中的 NoOpPasswordEncoder（secret 无需 {noop} 前缀）
        assertEquals(401, post(chain, TokenEndpointConfigurer.DEFAULT_PATH, "bad").getStatus());

        // 其他 POST 仍受 CSRF 保护
        assertEquals(403, post(chain, "/api/orders", "pw").getStatus());
    }

    @Test
    void customPathAndTimeToLive() throws Exception {
        Filter chain = chain(CustomConfig.class);
        MockHttpServletResponse ok = post(chain, "/oauth2/token", "pw");
        assertEquals(200, ok.getStatus(), ok.getContentAsString());
        assertEquals(600L, ((Number) JSONObjectUtils.parse(ok.getContentAsString()).get("expires_in")).longValue());
        assertEquals("custom", context.getBean(JwtFacade.class).parse(
                (String) JSONObjectUtils.parse(ok.getContentAsString()).get("access_token")).getClaim("tenant"));
        assertEquals(403, post(chain, TokenEndpointConfigurer.DEFAULT_PATH, "pw").getStatus());
    }

    @Test
    void missingRepositoryFailsFast() {
        Exception ex = assertThrows(Exception.class, () -> chain(MissingRepositoryConfig.class));
        Throwable cause = ex;
        while (cause.getCause() != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().contains("RegisteredClientRepository"), cause.getMessage());
    }

    static class Beans {
        @Bean
        JwtFacade jwtFacade() {
            return new JwtFacade(JwtConfig.builder().algorithm(JWSAlgorithm.RS256).expiration(1800).build());
        }

        @Bean
        @SuppressWarnings("deprecation")
        PasswordEncoder passwordEncoder() {
            return NoOpPasswordEncoder.getInstance();
        }
    }

    @Configuration
    @EnableWebSecurity
    static class DefaultsConfig extends Beans {
        @Bean
        RegisteredClientRepository registeredClientRepository() {
            return new InMemoryRegisteredClientRepository(RegisteredClient.withClientId("svc").clientSecret("pw").build());
        }

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            // CSRF 保持默认开启
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .with(new TokenEndpointConfigurer<>(), Customizer.withDefaults());
            return http.build();
        }
    }

    @Configuration
    @EnableWebSecurity
    static class CustomConfig extends Beans {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .with(new TokenEndpointConfigurer<HttpSecurity>()
                            .path("/oauth2/token")
                            .registeredClientRepository(new InMemoryRegisteredClientRepository(
                                    RegisteredClient.withClientId("svc").clientSecret("pw").build()))
                            .accessTokenTimeToLive(java.time.Duration.ofMinutes(10))
                            .claimsCustomizer((claims, client, scopes) -> claims.claim("tenant", "custom")),
                            Customizer.withDefaults());
            return http.build();
        }
    }

    @Configuration
    @EnableWebSecurity
    static class MissingRepositoryConfig extends Beans {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.with(new TokenEndpointConfigurer<>(), Customizer.withDefaults());
            return http.build();
        }
    }
}
