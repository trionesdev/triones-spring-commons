package com.trionesdev.spring.security.authorization.server.token;

import com.trionesdev.commons.core.jwt.JwtFacade;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;

/**
 * 把 token 端点（{@link TokenEndpointFilter}）挂到 Spring Security 过滤器链中：
 * <pre>{@code
 * http.with(new TokenEndpointConfigurer<>(), Customizer.withDefaults());
 * // 链式调用时需显式写出泛型参数
 * http.with(new TokenEndpointConfigurer<HttpSecurity>()
 *         .path("/oauth2/token")
 *         .issuer("https://auth.example.com")
 *         .accessTokenTimeToLive(Duration.ofMinutes(10)), Customizer.withDefaults());
 * }</pre>
 * 未指定时从容器（HttpSecurity 共享的 ApplicationContext）获取：
 * <ul>
 *     <li>{@link JwtFacade}、{@link RegisteredClientRepository}：必须存在；</li>
 *     <li>{@link PasswordEncoder}：存在唯一 Bean 时使用，否则使用
 *     {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}（secret 需带 {@code {bcrypt}}、{@code {noop}} 等前缀）。</li>
 * </ul>
 * 端点过滤器位于认证 / 授权过滤器之前并直接写出响应，无需 {@code permitAll()}；同时自动从 CSRF 保护中排除该端点。
 */
public class TokenEndpointConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractHttpConfigurer<TokenEndpointConfigurer<H>, H> {
    public static final String DEFAULT_PATH = "/auth/token";

    private String path = DEFAULT_PATH;
    private JwtFacade jwtFacade;
    private RegisteredClientRepository registeredClientRepository;
    private PasswordEncoder passwordEncoder;
    private String issuer;
    private Duration accessTokenTimeToLive;
    private TokenClaimsCustomizer claimsCustomizer;

    public TokenEndpointConfigurer<H> path(String path) {
        this.path = path;
        return this;
    }

    public TokenEndpointConfigurer<H> jwtFacade(JwtFacade jwtFacade) {
        this.jwtFacade = jwtFacade;
        return this;
    }

    public TokenEndpointConfigurer<H> registeredClientRepository(RegisteredClientRepository repository) {
        this.registeredClientRepository = repository;
        return this;
    }

    public TokenEndpointConfigurer<H> passwordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        return this;
    }

    /**
     * 写入 token 的 {@code iss}，资源服务器可据此校验签发方
     */
    public TokenEndpointConfigurer<H> issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    /**
     * 默认 access token 有效期（客户端未单独设置时），默认 30 分钟
     */
    public TokenEndpointConfigurer<H> accessTokenTimeToLive(Duration accessTokenTimeToLive) {
        this.accessTokenTimeToLive = accessTokenTimeToLive;
        return this;
    }

    public TokenEndpointConfigurer<H> claimsCustomizer(TokenClaimsCustomizer claimsCustomizer) {
        this.claimsCustomizer = claimsCustomizer;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(H http) {
        CsrfConfigurer<H> csrf = http.getConfigurer(CsrfConfigurer.class);
        if (csrf != null) {
            // token 端点由客户端凭据认证，不使用 Cookie 会话，无需 CSRF 保护
            csrf.ignoringRequestMatchers(endpointMatcher());
        }
    }

    @Override
    public void configure(H http) {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        ClientCredentialsTokenGenerator generator = new ClientCredentialsTokenGenerator(
                jwtFacade != null ? jwtFacade : requireBean(context, JwtFacade.class));
        generator.setIssuer(issuer);
        if (accessTokenTimeToLive != null) {
            generator.setDefaultAccessTokenTimeToLive(accessTokenTimeToLive);
        }
        generator.setClaimsCustomizer(claimsCustomizer);

        RegisteredClientRepository repository = registeredClientRepository != null
                ? registeredClientRepository : requireBean(context, RegisteredClientRepository.class);
        TokenEndpointFilter filter = new TokenEndpointFilter(path, repository, resolvePasswordEncoder(context), generator);
        http.addFilterBefore(postProcess(filter), UsernamePasswordAuthenticationFilter.class);
    }

    private RequestMatcher endpointMatcher() {
        String endpointPath = path;
        return request -> endpointPath.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    private PasswordEncoder resolvePasswordEncoder(ApplicationContext context) {
        if (passwordEncoder != null) {
            return passwordEncoder;
        }
        if (context != null) {
            PasswordEncoder bean = context.getBeanProvider(PasswordEncoder.class).getIfUnique();
            if (bean != null) {
                return bean;
            }
        }
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static <T> T requireBean(ApplicationContext context, Class<T> type) {
        T bean = context == null ? null : context.getBeanProvider(type).getIfUnique();
        if (bean == null) {
            throw new IllegalStateException(type.getSimpleName() + " is required: pass it to TokenEndpointConfigurer "
                    + "or register a single " + type.getSimpleName() + " bean");
        }
        return bean;
    }
}
