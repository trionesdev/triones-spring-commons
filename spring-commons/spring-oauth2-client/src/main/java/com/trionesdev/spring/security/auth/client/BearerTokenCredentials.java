package com.trionesdev.spring.security.auth.client;

/**
 * 携带原始 bearer token 的认证凭据。
 * <p>
 * 认证过滤器把它作为 {@link org.springframework.security.core.Authentication#getCredentials()} 放入
 * SecurityContext 后，{@link SecurityContextUserTokenResolver} 即可读到用户 token 并向下游透传。
 * 例如 spring-security 模块的 {@code JwtUserDetails}。OAuth2 资源服务器的认证（credentials 为 {@code Jwt} 等
 * {@code AbstractOAuth2Token}）无需实现本接口，resolver 直接支持。
 */
public interface BearerTokenCredentials {
    String getToken();
}
