package com.trionesdev.spring.security.jwks;

import com.trionesdev.commons.core.jwt.JwtFacade;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Duration;

/**
 * 把 JWKS 端点挂到 Spring Security 过滤器链中（与 Spring Authorization Server 的
 * {@code NimbusJwkSetEndpointFilter} 做法一致）。
 * <p>
 * 端点过滤器位于认证/授权过滤器之前并直接写出响应，因此无需 {@code permitAll()}；
 * 同时经过链中的 HeaderWriterFilter、CorsFilter 等，安全响应头与 CORS 配置照常生效。
 * <p>
 * 使用方式：
 * <pre>{@code
 * http.with(new JwksEndpointConfigurer<>(), Customizer.withDefaults());            // 默认路径与缓存时间
 * // 链式调用时需显式写出泛型参数，否则无法推断
 * http.with(new JwksEndpointConfigurer<HttpSecurity>().path("/oauth2/jwks"), Customizer.withDefaults());
 * }</pre>
 * 未指定 {@link JwtFacade} 时从容器（HttpSecurity 的共享 ApplicationContext）获取；
 * 未指定 path / cacheMaxAge 时使用 {@link #DEFAULT_PATH} / {@link #DEFAULT_CACHE_MAX_AGE}。
 */
public class JwksEndpointConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<JwksEndpointConfigurer<H>, H> {
    public static final String DEFAULT_PATH = "/auth/jwks";
    public static final Duration DEFAULT_CACHE_MAX_AGE = Duration.ofMinutes(5);

    private JwtFacade jwtFacade;
    private String path;
    private Duration cacheMaxAge;

    public JwksEndpointConfigurer() {
    }

    public JwksEndpointConfigurer(JwtFacade jwtFacade, String path, Duration cacheMaxAge) {
        this.jwtFacade = jwtFacade;
        this.path = path;
        this.cacheMaxAge = cacheMaxAge;
    }

    public JwksEndpointConfigurer<H> jwtFacade(JwtFacade jwtFacade) {
        this.jwtFacade = jwtFacade;
        return this;
    }

    public JwksEndpointConfigurer<H> path(String path) {
        this.path = path;
        return this;
    }

    public JwksEndpointConfigurer<H> cacheMaxAge(Duration cacheMaxAge) {
        this.cacheMaxAge = cacheMaxAge;
        return this;
    }

    @Override
    public void configure(H http) {
        JwtFacade facade = jwtFacade != null ? jwtFacade : resolveJwtFacade(http);
        String endpointPath = path != null ? path : DEFAULT_PATH;
        Duration maxAge = cacheMaxAge != null ? cacheMaxAge : DEFAULT_CACHE_MAX_AGE;
        // 位于 UsernamePasswordAuthenticationFilter 之前：早于 JwtAuthenticationFilter（其后）和 AuthorizationFilter（链尾）
        http.addFilterBefore(postProcess(new JwksEndpointFilter(facade, endpointPath, maxAge)),
                UsernamePasswordAuthenticationFilter.class);
    }

    private JwtFacade resolveJwtFacade(H http) {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        if (context == null) {
            throw new IllegalStateException("JwtFacade is required: pass it to JwksEndpointConfigurer "
                    + "or register a JwtFacade bean");
        }
        return context.getBean(JwtFacade.class);
    }
}
