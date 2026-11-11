package com.trionesdev.spring.security.auth.client;

import com.trionesdev.spring.security.auth.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizedClientHttpRequestInterceptorTest {

    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> seenAuthHeaders = new ArrayList<>();
    private AuthorizedClientHttpRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ClientRegistration registration = ClientRegistration.builder()
                .clientId("id").tokenUri("http://auth/token").build();
        AuthorizedClientManager manager = new AuthorizedClientManager(
                new InMemoryClientRegistrationRepository(Map.of("svc", registration)),
                reg -> AuthorizedClient.builder()
                        .clientRegistration(reg)
                        .accessToken(AccessToken.of("token-" + tokenCalls.incrementAndGet()))
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build());
        interceptor = new AuthorizedClientHttpRequestInterceptor(manager, "svc");
    }

    private ClientHttpRequestExecution respondWith(HttpStatus status) {
        return (request, body) -> {
            seenAuthHeaders.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return new FakeResponse(status);
        };
    }

    @Test
    void addsBearerToken() throws Exception {
        interceptor.intercept(new FakeRequest(), new byte[0], respondWith(HttpStatus.OK));
        interceptor.intercept(new FakeRequest(), new byte[0], respondWith(HttpStatus.OK));
        assertEquals(List.of("Bearer token-1", "Bearer token-1"), seenAuthHeaders);
    }

    @Test
    void unauthorizedEvictsTokenForNextRequest() throws Exception {
        ClientHttpResponse response =
                interceptor.intercept(new FakeRequest(), new byte[0], respondWith(HttpStatus.UNAUTHORIZED));
        assertEquals(401, response.getStatusCode().value());
        interceptor.intercept(new FakeRequest(), new byte[0], respondWith(HttpStatus.OK));
        assertEquals(List.of("Bearer token-1", "Bearer token-2"), seenAuthHeaders);
    }

    @Test
    void keepsExistingAuthorizationHeader() throws Exception {
        FakeRequest request = new FakeRequest();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        interceptor.intercept(request, new byte[0], respondWith(HttpStatus.OK));
        assertEquals(List.of("Bearer user-token"), seenAuthHeaders);
        assertEquals(0, tokenCalls.get());
    }

    static class FakeRequest implements HttpRequest {
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create("http://downstream.local/api");
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    static class FakeResponse implements ClientHttpResponse {
        private final HttpStatus status;

        FakeResponse(HttpStatus status) {
            this.status = status;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    }
}
