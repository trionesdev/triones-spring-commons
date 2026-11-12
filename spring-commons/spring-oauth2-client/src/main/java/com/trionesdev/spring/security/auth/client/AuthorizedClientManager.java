package com.trionesdev.spring.security.auth.client;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务间调用的 token 管理器（类似 Spring Security 的 OAuth2AuthorizedClientManager，仅 client_credentials）。
 * <p>
 * 没有用户上下文时，以服务自身身份向授权服务器申请 token，并按 registrationId 缓存，过期前自动重新申请。
 * <pre>{@code
 * AuthorizedClient client = manager.authorize(AuthorizeRequest.of("order-service"));
 * request.header("Authorization", client.getAuthorizationHeaderValue());
 * }</pre>
 */
public class AuthorizedClientManager {
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final ClientCredentialsTokenResponseClient tokenResponseClient;
    private final Map<String, AuthorizedClient> authorizedClients = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private Duration clockSkew = Duration.ofSeconds(60);
    private Clock clock = Clock.systemUTC();

    public AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository) {
        this(clientRegistrationRepository, new OkHttpClientCredentialsTokenResponseClient());
    }

    public AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                   ClientCredentialsTokenResponseClient tokenResponseClient) {
        this.clientRegistrationRepository = Objects.requireNonNull(clientRegistrationRepository, "clientRegistrationRepository");
        this.tokenResponseClient = Objects.requireNonNull(tokenResponseClient, "tokenResponseClient");
    }

    /**
     * 获取可用的授权客户端；缓存中无 token 或即将过期时重新申请。
     */
    public AuthorizedClient authorize(AuthorizeRequest request) {
        Objects.requireNonNull(request, "request");
        String registrationId = request.getClientRegistrationId();
        AuthorizedClient cached = authorizedClients.get(registrationId);
        if (isUsable(cached)) {
            return cached;
        }
        // 同一 registrationId 串行申请，避免并发时重复请求授权服务器
        synchronized (locks.computeIfAbsent(registrationId, id -> new Object())) {
            cached = authorizedClients.get(registrationId);
            if (isUsable(cached)) {
                return cached;
            }
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
            if (registration == null) {
                throw new AuthorizedClientException("Client registration not found: " + registrationId);
            }
            AuthorizedClient authorized = tokenResponseClient.getTokenResponse(registration);
            authorizedClients.put(registrationId, authorized);
            return authorized;
        }
    }

    /**
     * @return 仅 access token 值
     */
    public String getAccessToken(String registrationId) {
        return authorize(AuthorizeRequest.of(registrationId)).getAccessToken().getTokenValue();
    }

    /**
     * 移除缓存的 token，例如下游返回 401 时调用，下次 authorize 会重新申请。
     */
    public void remove(String registrationId) {
        authorizedClients.remove(registrationId);
    }

    public void setClockSkew(Duration clockSkew) {
        Objects.requireNonNull(clockSkew, "clockSkew");
        if (clockSkew.isNegative()) {
            throw new IllegalArgumentException("clockSkew must be >= 0");
        }
        this.clockSkew = clockSkew;
    }

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private boolean isUsable(AuthorizedClient client) {
        return client != null && !client.isExpired(clock.instant(), clockSkew);
    }
}
