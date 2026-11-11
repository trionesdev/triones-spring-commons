package com.trionesdev.spring.security.client;

import com.trionesdev.spring.security.jwt.JwtAuthenticationToken;
import com.trionesdev.spring.security.jwt.JwtUserDetails;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizedClientFeignRequestInterceptorTest {

    private final AtomicInteger tokenCalls = new AtomicInteger();
    private AuthorizedClientManager manager;

    @BeforeEach
    void setUp() {
        ClientRegistration registration = ClientRegistration.builder()
                .clientId("id").tokenUri("http://auth/token").build();
        manager = new AuthorizedClientManager(
                new InMemoryClientRegistrationRepository(Map.of("svc", registration)),
                reg -> AuthorizedClient.builder()
                        .clientRegistration(reg)
                        .accessToken(AccessToken.of("client-token-" + tokenCalls.incrementAndGet()))
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String token) {
        JwtUserDetails details = JwtUserDetails.builder().actorId("u1").token(token).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken("u1", details, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static Collection<String> authorization(RequestTemplate template) {
        return template.headers().get("Authorization");
    }

    @Test
    void relaysUserTokenWhenUserPresent() {
        loginAs("user-token");
        RequestTemplate template = new RequestTemplate();
        new AuthorizedClientFeignRequestInterceptor(manager, "svc").apply(template);
        assertEquals(List.of("Bearer user-token"), List.copyOf(authorization(template)));
        assertEquals(0, tokenCalls.get());
    }

    @Test
    void usesClientTokenWithoutUser() {
        RequestTemplate template = new RequestTemplate();
        AuthorizedClientFeignRequestInterceptor interceptor = new AuthorizedClientFeignRequestInterceptor(manager, "svc");
        interceptor.apply(template);
        interceptor.apply(new RequestTemplate());
        assertEquals(List.of("Bearer client-token-1"), List.copyOf(authorization(template)));
        assertEquals(1, tokenCalls.get());
    }

    @Test
    void nonJwtAuthenticationFallsBackToClientToken() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("u", "pwd", List.of()));
        SecurityContextHolder.setContext(context);
        RequestTemplate template = new RequestTemplate();
        new AuthorizedClientFeignRequestInterceptor(manager, "svc").apply(template);
        assertEquals(List.of("Bearer client-token-1"), List.copyOf(authorization(template)));
    }

    @Test
    void relayDisabledAlwaysUsesClientToken() {
        loginAs("user-token");
        RequestTemplate template = new RequestTemplate();
        AuthorizedClientFeignRequestInterceptor interceptor = new AuthorizedClientFeignRequestInterceptor(manager, "svc");
        interceptor.setRelayUserToken(false);
        interceptor.apply(template);
        assertEquals(List.of("Bearer client-token-1"), List.copyOf(authorization(template)));
    }

    @Test
    void keepsExplicitAuthorizationHeader() {
        loginAs("user-token");
        RequestTemplate template = new RequestTemplate().header("Authorization", "Bearer explicit");
        new AuthorizedClientFeignRequestInterceptor(manager, "svc").apply(template);
        assertEquals(List.of("Bearer explicit"), List.copyOf(authorization(template)));
        assertEquals(0, tokenCalls.get());
    }

    @Test
    void relayOnlyModeWithoutManager() {
        AuthorizedClientFeignRequestInterceptor interceptor =
                new AuthorizedClientFeignRequestInterceptor(null, null, new SecurityContextUserTokenResolver());
        RequestTemplate anonymous = new RequestTemplate();
        interceptor.apply(anonymous);
        assertNull(authorization(anonymous));

        loginAs("user-token");
        RequestTemplate withUser = new RequestTemplate();
        interceptor.apply(withUser);
        assertEquals(List.of("Bearer user-token"), List.copyOf(authorization(withUser)));
    }

    @Test
    void customUserTokenResolver() {
        RequestTemplate template = new RequestTemplate();
        new AuthorizedClientFeignRequestInterceptor(manager, "svc", () -> "from-header").apply(template);
        assertEquals(List.of("Bearer from-header"), List.copyOf(authorization(template)));
    }

    @Test
    void managerWithoutRegistrationIdRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuthorizedClientFeignRequestInterceptor(manager, null, () -> null));
    }
}
