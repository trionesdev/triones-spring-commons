package com.trionesdev.spring.security.auth.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * RestTemplate / RestClient 拦截器：自动附加 client_credentials token。
 * 收到 401 时清除缓存的 token，下次请求重新申请（请求体可能是流，这里不做自动重试）。
 */
public class AuthorizedClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private final AuthorizedClientManager manager;
    private final String registrationId;

    public AuthorizedClientHttpRequestInterceptor(AuthorizedClientManager manager, String registrationId) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                    manager.authorize(AuthorizeRequest.of(registrationId)).getAuthorizationHeaderValue());
        }
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getStatusCode().value() == 401) {
            manager.remove(registrationId);
        }
        return response;
    }
}
