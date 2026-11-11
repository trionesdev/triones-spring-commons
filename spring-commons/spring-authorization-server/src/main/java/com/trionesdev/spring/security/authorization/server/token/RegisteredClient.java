package com.trionesdev.spring.security.authorization.server.token;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 授权服务器上登记的客户端（client_credentials 模式）。不可变，通过 {@link #withClientId(String)} 构建：
 * <pre>{@code
 * RegisteredClient.withClientId("order-service")
 *         .clientSecret("{bcrypt}$2a$10$...")      // PasswordEncoder 编码后的值，开发环境可用 {noop}secret
 *         .scopes("order.read", "order.write")
 *         .accessTokenTimeToLive(Duration.ofMinutes(30))
 *         .build();
 * }</pre>
 */
public final class RegisteredClient {
    private final String clientId;
    private final String clientSecret;
    private final Set<ClientAuthenticationMethod> clientAuthenticationMethods;
    private final Set<String> scopes;
    private final Duration accessTokenTimeToLive;

    private RegisteredClient(Builder builder) {
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.clientAuthenticationMethods = Collections.unmodifiableSet(EnumSet.copyOf(builder.clientAuthenticationMethods));
        this.scopes = Collections.unmodifiableSet(new LinkedHashSet<>(builder.scopes));
        this.accessTokenTimeToLive = builder.accessTokenTimeToLive;
    }

    public static Builder withClientId(String clientId) {
        return new Builder(clientId);
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * {@link org.springframework.security.crypto.password.PasswordEncoder} 编码后的 secret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    public Set<ClientAuthenticationMethod> getClientAuthenticationMethods() {
        return clientAuthenticationMethods;
    }

    /**
     * 允许申请的 scope
     */
    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * access token 有效期；null 时使用 token 端点的默认值
     */
    public Duration getAccessTokenTimeToLive() {
        return accessTokenTimeToLive;
    }

    @Override
    public String toString() {
        // 不输出 secret
        return "RegisteredClient{clientId='" + clientId + "', clientAuthenticationMethods=" + clientAuthenticationMethods
                + ", scopes=" + scopes + ", accessTokenTimeToLive=" + accessTokenTimeToLive + '}';
    }

    public static final class Builder {
        private final String clientId;
        private String clientSecret;
        private Set<ClientAuthenticationMethod> clientAuthenticationMethods =
                EnumSet.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, ClientAuthenticationMethod.CLIENT_SECRET_POST);
        private final Set<String> scopes = new LinkedHashSet<>();
        private Duration accessTokenTimeToLive;

        private Builder(String clientId) {
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("clientId must not be empty");
            }
            this.clientId = clientId;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /**
         * 允许的认证方式，默认 basic 与 post 均可
         */
        public Builder clientAuthenticationMethods(ClientAuthenticationMethod... methods) {
            this.clientAuthenticationMethods = methods.length == 0
                    ? EnumSet.noneOf(ClientAuthenticationMethod.class) : EnumSet.of(methods[0], methods);
            return this;
        }

        public Builder scopes(String... scopes) {
            return scopes(java.util.Arrays.asList(scopes));
        }

        public Builder scopes(Collection<String> scopes) {
            if (scopes != null) {
                for (String scope : scopes) {
                    if (scope == null || scope.isBlank() || scope.chars().anyMatch(Character::isWhitespace)) {
                        throw new IllegalArgumentException("Invalid scope '" + scope + "' for client " + clientId);
                    }
                    this.scopes.add(scope);
                }
            }
            return this;
        }

        public Builder accessTokenTimeToLive(Duration accessTokenTimeToLive) {
            if (accessTokenTimeToLive != null && (accessTokenTimeToLive.isZero() || accessTokenTimeToLive.isNegative())) {
                throw new IllegalArgumentException("accessTokenTimeToLive must be positive");
            }
            this.accessTokenTimeToLive = accessTokenTimeToLive;
            return this;
        }

        public RegisteredClient build() {
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new IllegalArgumentException("clientSecret is required for client " + clientId);
            }
            if (clientAuthenticationMethods.isEmpty()) {
                throw new IllegalArgumentException("clientAuthenticationMethods must not be empty for client " + clientId);
            }
            Objects.requireNonNull(scopes);
            return new RegisteredClient(this);
        }
    }
}
