package com.trionesdev.spring.security.auth.client;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 {@link SecurityContextHolder} 中读取当前用户的 bearer token：要求已认证，且 credentials 实现了
 * {@link BearerTokenCredentials}（例如 spring-security 模块 {@code JwtAuthenticationFilter} 放入的 {@code JwtUserDetails}）。
 * <p>
 * SecurityContext 默认是线程本地的：在 @Async、线程池、MQ 消费、定时任务等线程中没有用户信息，此时返回 null。
 */
public class SecurityContextUserTokenResolver implements UserTokenResolver {

    @Override
    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getCredentials() instanceof BearerTokenCredentials credentials) {
            String token = credentials.getToken();
            return token == null || token.isBlank() ? null : token.trim();
        }
        return null;
    }
}
