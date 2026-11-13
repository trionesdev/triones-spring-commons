package com.trionesdev.spring.security.auth.client;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.util.ClassUtils;

/**
 * 从 {@link SecurityContextHolder} 中读取当前用户的 bearer token，要求已认证，且 credentials 为：
 * <ul>
 *     <li>{@link BearerTokenCredentials}：spring-security 模块 {@code JwtAuthenticationFilter} 放入的 {@code JwtUserDetails}；</li>
 *     <li>{@code AbstractOAuth2Token}：OAuth2 资源服务器的 {@code JwtAuthenticationToken}（含 spring-security-oauth2 的
 *     {@code ActorJwtAuthenticationToken}）、{@code BearerTokenAuthentication}，需 classpath 上有 spring-security-oauth2-core。</li>
 * </ul>
 * SecurityContext 默认是线程本地的：在 @Async、线程池、MQ 消费、定时任务等线程中没有用户信息，此时返回 null。
 */
public class SecurityContextUserTokenResolver implements UserTokenResolver {
    private static final boolean OAUTH2_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.oauth2.core.AbstractOAuth2Token",
            SecurityContextUserTokenResolver.class.getClassLoader());

    @Override
    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object credentials = authentication.getCredentials();
        String token = null;
        if (credentials instanceof BearerTokenCredentials bearer) {
            token = bearer.getToken();
        } else if (OAUTH2_PRESENT) {
            token = OAuth2TokenValue.of(credentials);
        }
        return token == null || token.isBlank() ? null : token.trim();
    }

    /**
     * 单独的类：spring-security-oauth2-core 不在 classpath 时不会被加载
     */
    private static final class OAuth2TokenValue {
        static String of(Object credentials) {
            return credentials instanceof AbstractOAuth2Token token ? token.getTokenValue() : null;
        }
    }
}
