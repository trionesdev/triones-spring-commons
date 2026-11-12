package com.trionesdev.spring.security.auth.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 已授权的客户端（类似 Spring Security 的 OAuth2AuthorizedClient），持有通过 client_credentials 获取的 access token。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthorizedClient {
    private ClientRegistration clientRegistration;
    private AccessToken accessToken;
    private RefreshToken refreshToken;
    private String tokenType;
    private Instant issuedAt;
    /**
     * 过期时间，授权服务器未返回 expires_in 时为 null（视为不过期）
     */
    private Instant expiresAt;
    private Set<String> scopes;

    public String getRegistrationId() {
        return clientRegistration == null ? null : clientRegistration.getRegistrationId();
    }

    /**
     * @param clockSkew 提前刷新的时间窗口，避免 token 在请求途中过期
     */
    public boolean isExpired(Instant now, Duration clockSkew) {
        return expiresAt != null && !now.plus(clockSkew).isBefore(expiresAt);
    }

    /**
     * @return 可直接放入 Authorization 头的值，例如 {@code Bearer xxx}
     */
    public String getAuthorizationHeaderValue() {
        String type = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        if ("bearer".equalsIgnoreCase(type)) {
            type = "Bearer";
        }
        return type + " " + (accessToken == null ? null : accessToken.getTokenValue());
    }
}
