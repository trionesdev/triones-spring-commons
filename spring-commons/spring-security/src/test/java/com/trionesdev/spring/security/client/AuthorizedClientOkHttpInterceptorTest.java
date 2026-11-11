package com.trionesdev.spring.security.client;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizedClientOkHttpInterceptorTest {

    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> seenAuthHeaders = new ArrayList<>();
    private final List<Integer> statuses = new ArrayList<>();
    private AuthorizedClientManager manager;

    @BeforeEach
    void setUp() {
        ClientRegistration registration = ClientRegistration.builder()
                .clientId("id").tokenUri("http://auth/token").build();
        manager = new AuthorizedClientManager(
                new InMemoryClientRegistrationRepository(Map.of("svc", registration)),
                reg -> AuthorizedClient.builder()
                        .clientRegistration(reg)
                        .accessToken(AccessToken.of("token-" + tokenCalls.incrementAndGet()))
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build());
    }

    private OkHttpClient http() {
        return new OkHttpClient.Builder()
                .addInterceptor(new AuthorizedClientOkHttpInterceptor(manager, "svc"))
                .addInterceptor(chain -> {
                    seenAuthHeaders.add(chain.request().header("Authorization"));
                    int status = statuses.isEmpty() ? 200 : statuses.remove(0);
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message("fake")
                            .body(ResponseBody.create("", MediaType.get("text/plain")))
                            .build();
                })
                .build();
    }

    private int call(Request request) throws Exception {
        try (Response response = http().newCall(request).execute()) {
            return response.code();
        }
    }

    private Request request() {
        return new Request.Builder().url("http://downstream.local/api").build();
    }

    @Test
    void addsBearerTokenAndReusesIt() throws Exception {
        assertEquals(200, call(request()));
        assertEquals(200, call(request()));
        assertEquals(List.of("Bearer token-1", "Bearer token-1"), seenAuthHeaders);
        assertEquals(1, tokenCalls.get());
    }

    @Test
    void retriesOnceWithFreshTokenOn401() throws Exception {
        statuses.add(401);
        assertEquals(200, call(request()));
        assertEquals(List.of("Bearer token-1", "Bearer token-2"), seenAuthHeaders);
    }

    @Test
    void doesNotRetryMoreThanOnce() throws Exception {
        statuses.add(401);
        statuses.add(401);
        assertEquals(401, call(request()));
        assertEquals(2, seenAuthHeaders.size());
    }

    @Test
    void keepsExistingAuthorizationHeader() throws Exception {
        Request request = request().newBuilder().header("Authorization", "Bearer user-token").build();
        assertEquals(200, call(request));
        assertEquals(List.of("Bearer user-token"), seenAuthHeaders);
        assertEquals(0, tokenCalls.get());
    }
}
