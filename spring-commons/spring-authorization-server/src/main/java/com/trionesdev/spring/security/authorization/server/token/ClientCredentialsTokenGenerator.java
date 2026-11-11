package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.core.jwt.ClaimsKeyConstant;
import com.trionesdev.commons.core.jwt.JwtFacade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 用 {@link JwtFacade} 为 client_credentials 请求签发 JWT access token。
 * <p>
 * 默认 claims：
 * <ul>
 *     <li>{@code iss}：配置了 issuer 时写入；</li>
 *     <li>{@code sub}、{@code client_id}：clientId；</li>
 *     <li>{@code scope}：授予的 scope，空格分隔（RFC 9068），为空时不写；</li>
 *     <li>{@code ACTOR_ID}：clientId，资源服务器的 DefaultJwtActorResolver 据此得到服务身份
 *     （没有 ACTOR_USER_ID，可据此区分服务调用与用户调用）；</li>
 *     <li>{@code iat}、{@code exp}、{@code jti}。</li>
 * </ul>
 */
public class ClientCredentialsTokenGenerator {
    public static final Duration DEFAULT_ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(30);

    private final JwtFacade jwtFacade;
    private String issuer;
    private Duration defaultAccessTokenTimeToLive = DEFAULT_ACCESS_TOKEN_TIME_TO_LIVE;
    private TokenClaimsCustomizer claimsCustomizer;
    private Clock clock = Clock.systemUTC();

    public ClientCredentialsTokenGenerator(JwtFacade jwtFacade) {
        this.jwtFacade = Objects.requireNonNull(jwtFacade, "jwtFacade");
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer == null || issuer.isBlank() ? null : issuer;
    }

    public void setDefaultAccessTokenTimeToLive(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("defaultAccessTokenTimeToLive must be positive");
        }
        this.defaultAccessTokenTimeToLive = ttl;
    }

    public void setClaimsCustomizer(TokenClaimsCustomizer claimsCustomizer) {
        this.claimsCustomizer = claimsCustomizer;
    }

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public IssuedToken generate(RegisteredClient client, Set<String> authorizedScopes) {
        Objects.requireNonNull(client, "client");
        Duration ttl = client.getAccessTokenTimeToLive() != null
                ? client.getAccessTokenTimeToLive() : defaultAccessTokenTimeToLive;
        // JWT 时间精确到秒，避免 expires_in 与 exp 不一致
        Instant issuedAt = Instant.ofEpochSecond(clock.instant().getEpochSecond());
        Instant expiresAt = issuedAt.plus(ttl);

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(client.getClientId())
                .claim("client_id", client.getClientId())
                .claim(ClaimsKeyConstant.ACTOR_ID, client.getClientId())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (authorizedScopes != null && !authorizedScopes.isEmpty()) {
            claims.claim("scope", String.join(" ", authorizedScopes));
        }
        if (claimsCustomizer != null) {
            claimsCustomizer.customize(claims, client, authorizedScopes);
        }
        String token = jwtFacade.generate(claims.build());
        // 保持 scope 顺序
        Set<String> scopes = authorizedScopes == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorizedScopes));
        return new IssuedToken(token, issuedAt, expiresAt, scopes);
    }

    /**
     * @param scopes 授予的 scope
     */
    public record IssuedToken(String tokenValue, Instant issuedAt, Instant expiresAt, Set<String> scopes) {
        public long expiresIn() {
            return Math.max(0, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());
        }
    }
}
