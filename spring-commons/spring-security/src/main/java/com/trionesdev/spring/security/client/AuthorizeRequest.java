package com.trionesdev.spring.security.client;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * 授权请求（类似 Spring Security 的 OAuth2AuthorizeRequest）。
 * <pre>{@code
 * manager.authorize(AuthorizeRequest.withClientRegistrationId("order-service").principal("order-job").build());
 * }</pre>
 */
@Getter
@ToString
public class AuthorizeRequest {
    private final String clientRegistrationId;
    /**
     * 发起调用的主体（如服务名、任务名），仅用于标识/日志；
     * client_credentials 的 token 只与客户端相关，缓存按 clientRegistrationId 共享。
     */
    private final String principal;

    @Builder
    private AuthorizeRequest(String clientRegistrationId, String principal) {
        this.clientRegistrationId = Objects.requireNonNull(clientRegistrationId, "clientRegistrationId");
        this.principal = principal;
    }

    public static AuthorizeRequestBuilder withClientRegistrationId(String clientRegistrationId) {
        return builder().clientRegistrationId(clientRegistrationId);
    }

    public static AuthorizeRequest of(String clientRegistrationId) {
        return withClientRegistrationId(clientRegistrationId).build();
    }
}
