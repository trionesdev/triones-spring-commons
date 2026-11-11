package com.trionesdev.spring.security.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import java.util.Collection;
import java.util.Objects;

/**
 * Feign 拦截器，同时处理有用户信息和没有用户信息两种场景：
 * <ul>
 *     <li>请求已显式设置 Authorization 头：不做修改；</li>
 *     <li>当前线程有用户 token（{@link UserTokenResolver} 返回非空）且开启透传：透传用户 token，下游拿到的是用户身份；</li>
 *     <li>否则（定时任务、MQ 消费、异步线程等）：通过 {@link AuthorizedClientManager} 以 client_credentials
 *     申请服务 token，下游拿到的是服务身份。</li>
 * </ul>
 * <pre>{@code
 * @Bean
 * public RequestInterceptor orderServiceAuthInterceptor(AuthorizedClientManager manager) {
 *     return new AuthorizedClientFeignRequestInterceptor(manager, "order-service");
 * }
 * }</pre>
 */
public class AuthorizedClientFeignRequestInterceptor implements RequestInterceptor {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthorizedClientManager manager;
    private final String registrationId;
    private final UserTokenResolver userTokenResolver;
    /**
     * 有用户信息时是否透传用户 token，默认 true；设为 false 则始终使用服务 token
     */
    private boolean relayUserToken = true;

    public AuthorizedClientFeignRequestInterceptor(AuthorizedClientManager manager, String registrationId) {
        this(manager, registrationId, new SecurityContextUserTokenResolver());
    }

    /**
     * @param manager        为 null 时只透传用户 token，没有用户信息则不加 Authorization 头
     * @param registrationId 申请服务 token 使用的客户端，manager 不为 null 时必填
     */
    public AuthorizedClientFeignRequestInterceptor(AuthorizedClientManager manager, String registrationId,
                                                   UserTokenResolver userTokenResolver) {
        if (manager != null) {
            Objects.requireNonNull(registrationId, "registrationId");
        }
        this.manager = manager;
        this.registrationId = registrationId;
        this.userTokenResolver = Objects.requireNonNull(userTokenResolver, "userTokenResolver");
    }

    public void setRelayUserToken(boolean relayUserToken) {
        this.relayUserToken = relayUserToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (hasAuthorization(template)) {
            return;
        }
        if (relayUserToken) {
            String userToken = userTokenResolver.resolve();
            if (userToken != null && !userToken.isBlank()) {
                template.header(AUTHORIZATION, BEARER_PREFIX + userToken);
                return;
            }
        }
        if (manager != null) {
            AuthorizedClient client = manager.authorize(AuthorizeRequest.of(registrationId));
            template.header(AUTHORIZATION, client.getAuthorizationHeaderValue());
        }
    }

    private boolean hasAuthorization(RequestTemplate template) {
        Collection<String> values = template.headers().get(AUTHORIZATION);
        if (values == null) {
            // 兼容大小写不同的写法
            values = template.headers().entrySet().stream()
                    .filter(e -> AUTHORIZATION.equalsIgnoreCase(e.getKey()))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
        return values != null && values.stream().anyMatch(v -> v != null && !v.isBlank());
    }
}
