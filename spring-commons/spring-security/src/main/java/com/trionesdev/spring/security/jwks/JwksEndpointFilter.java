package com.trionesdev.spring.security.jwks;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.trionesdev.commons.core.jwt.JwtFacade;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * 返回 {@link JwtFacade#getPublicJwkSet()} 的 JWKS 端点（RFC 7517 §5），只含公钥。
 * <p>
 * 通过 {@link JwksEndpointConfigurer} 加入 Spring Security 过滤器链使用，不要注册为 Spring Bean
 * （Spring Boot 会把 Filter Bean 自动注册为全局 Servlet Filter，从而绕过安全链）。
 */
public class JwksEndpointFilter extends OncePerRequestFilter {
    private final String path;
    private final byte[] body;
    private final String cacheControl;

    public JwksEndpointFilter(JwtFacade jwtFacade, String path, Duration cacheMaxAge) {
        Objects.requireNonNull(jwtFacade, "jwtFacade");
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("triones.security.jwt.jwks.path must start with '/': " + path);
        }
        this.path = path;
        try {
            // 密钥在 JwtFacade 构造后不可变，启动时序列化一次
            this.body = JSONObjectUtils.toJSONString(jwtFacade.getPublicJwkSet()).getBytes(StandardCharsets.UTF_8);
        } catch (UnsupportedOperationException ex) {
            throw new IllegalStateException("triones.security.jwt.jwks is only available for RSA/EC algorithms; "
                    + "HMAC keys must not be published", ex);
        }
        long seconds = cacheMaxAge == null ? 0 : Math.max(0, cacheMaxAge.toSeconds());
        this.cacheControl = seconds > 0 ? "public, max-age=" + seconds : "no-store";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        boolean head = HttpMethod.HEAD.matches(method);
        if (!HttpMethod.GET.matches(method) && !head) {
            response.setHeader(HttpHeaders.ALLOW, "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        // 与 Spring Authorization Server 一致使用 application/json，兼容只接受 JSON 的客户端
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
        response.setContentLength(body.length);
        if (!head) {
            response.getOutputStream().write(body);
        }
    }
}
