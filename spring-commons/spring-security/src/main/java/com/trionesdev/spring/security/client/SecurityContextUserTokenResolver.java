package com.trionesdev.spring.security.client;

import com.trionesdev.spring.security.jwt.JwtAuthenticationToken;
import com.trionesdev.spring.security.jwt.JwtUserDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 {@link SecurityContextHolder} 中读取 {@link com.trionesdev.spring.security.jwt.JwtAuthenticationFilter}
 * 认证后的用户 token。
 * <p>
 * SecurityContext 默认是线程本地的：在 @Async、线程池、MQ 消费、定时任务等线程中没有用户信息，此时返回 null。
 */
public class SecurityContextUserTokenResolver implements UserTokenResolver {

    @Override
    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getCredentials() instanceof JwtUserDetails details) {
            return StringUtils.trimToNull(details.getToken());
        }
        return null;
    }
}
