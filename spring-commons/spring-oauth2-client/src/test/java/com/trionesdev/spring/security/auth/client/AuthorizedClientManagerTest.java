package com.trionesdev.spring.security.auth.client;

import com.trionesdev.spring.security.auth.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizedClientManagerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final AtomicInteger calls = new AtomicInteger();
    private final long expiresInSeconds = 300;
    private MutableClock clock;
    private AuthorizedClientManager manager;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        ClientRegistration registration = ClientRegistration.builder()
                .registrationId("svc").clientId("id").clientSecret("secret").tokenUri("http://auth/token").build();
        ClientCredentialsTokenResponseClient tokenClient = reg -> {
            int n = calls.incrementAndGet();
            return AuthorizedClient.builder()
                    .clientRegistration(reg)
                    .accessToken(AccessToken.of("token-" + n))
                    .tokenType("bearer")
                    .issuedAt(clock.instant())
                    .expiresAt(clock.instant().plusSeconds(expiresInSeconds))
                    .build();
        };
        manager = new AuthorizedClientManager(new InMemoryClientRegistrationRepository(List.of(registration)), tokenClient);
        manager.setClock(clock);
    }

    @Test
    void cachesTokenUntilNearExpiry() {
        assertEquals("token-1", manager.getAccessToken("svc"));
        clock.advance(Duration.ofSeconds(200));
        assertEquals("token-1", manager.getAccessToken("svc"));
        assertEquals(1, calls.get());
    }

    @Test
    void refreshesWithinClockSkew() {
        manager.getAccessToken("svc");
        // 300s expiry, 60s default skew -> refresh at 240s
        clock.advance(Duration.ofSeconds(240));
        assertEquals("token-2", manager.getAccessToken("svc"));
    }

    @Test
    void customClockSkew() {
        manager.setClockSkew(Duration.ZERO);
        manager.getAccessToken("svc");
        clock.advance(Duration.ofSeconds(299));
        assertEquals("token-1", manager.getAccessToken("svc"));
        clock.advance(Duration.ofSeconds(1));
        assertEquals("token-2", manager.getAccessToken("svc"));
    }

    @Test
    void removeForcesNewToken() {
        manager.getAccessToken("svc");
        manager.remove("svc");
        assertEquals("token-2", manager.getAccessToken("svc"));
    }

    @Test
    void unknownRegistrationThrows() {
        assertThrows(AuthorizedClientException.class, () -> manager.authorize(AuthorizeRequest.of("missing")));
    }

    @Test
    void negativeClockSkewRejected() {
        assertThrows(IllegalArgumentException.class, () -> manager.setClockSkew(Duration.ofSeconds(-1)));
    }

    @Test
    void concurrentAuthorizeRequestsTokenOnce() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return manager.getAccessToken("svc");
            }));
        }
        start.countDown();
        for (Future<String> f : results) {
            assertEquals("token-1", f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, calls.get());
    }

    @Test
    void authorizationHeaderNormalizesBearer() {
        AuthorizedClient client = manager.authorize(AuthorizeRequest.of("svc"));
        assertEquals("Bearer token-1", client.getAuthorizationHeaderValue());
        client.setTokenType(null);
        assertEquals("Bearer token-1", client.getAuthorizationHeaderValue());
    }

    @Test
    void tokenWithoutExpiryNeverExpires() {
        AuthorizedClient client = AuthorizedClient.builder().accessToken(AccessToken.of("t")).build();
        assertFalse(client.isExpired(Instant.MAX.minusSeconds(120), Duration.ofSeconds(60)));
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
