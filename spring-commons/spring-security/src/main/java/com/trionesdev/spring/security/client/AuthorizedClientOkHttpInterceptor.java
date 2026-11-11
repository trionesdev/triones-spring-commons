package com.trionesdev.spring.security.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/**
 * OkHttp 拦截器：自动为服务间请求附加 client_credentials token；收到 401 时刷新 token 重试一次。
 * <pre>{@code
 * new OkHttpClient.Builder()
 *     .addInterceptor(new AuthorizedClientOkHttpInterceptor(manager, "order-service"))
 *     .build();
 * }</pre>
 */
public class AuthorizedClientOkHttpInterceptor implements Interceptor {
    private final AuthorizedClientManager manager;
    private final String registrationId;

    public AuthorizedClientOkHttpInterceptor(AuthorizedClientManager manager, String registrationId) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId");
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request original = chain.request();
        if (original.header("Authorization") != null) {
            return chain.proceed(original);
        }
        AuthorizedClient client = manager.authorize(AuthorizeRequest.of(registrationId));
        Response response = chain.proceed(withToken(original, client));
        if (response.code() == 401) {
            response.close();
            manager.remove(registrationId);
            return chain.proceed(withToken(original, manager.authorize(AuthorizeRequest.of(registrationId))));
        }
        return response;
    }

    private Request withToken(Request request, AuthorizedClient client) {
        return request.newBuilder().header("Authorization", client.getAuthorizationHeaderValue()).build();
    }
}
