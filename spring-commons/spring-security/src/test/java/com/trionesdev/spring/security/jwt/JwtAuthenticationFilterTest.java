package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.spring.security.ResourceServerConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.trionesdev.commons.context.actor.ActorConstants.X_TRACE_ID;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    @Test
    void verifiesRemoteTokensAndCachesPublicKeys() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwt/issuer", exchange -> {
            requests.incrementAndGet();
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            JwtTokenConfig config = new JwtTokenConfig();
            ResourceServerConfig resourceServerConfig = new ResourceServerConfig();

            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(resourceServerConfig, null, new ActorContext(), null, null);
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("user-1")
                    .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
            String token = sign(key, claims);
            assertEquals("user-1", filter.remoteClaims(token).get("sub"));
            assertEquals("user-1", filter.remoteClaims(token).get("sub"));
            assertEquals(1, requests.get());

            RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            assertThrows(IOException.class, () -> filter.remoteClaims(sign(otherKey, claims)));
            JWTClaimsSet expired = new JWTClaimsSet.Builder().subject("user-1")
                    .expirationTime(Date.from(Instant.now().minusSeconds(300))).build();
            assertThrows(IOException.class, () -> filter.remoteClaims(sign(key, expired)));
            assertThrows(IOException.class, () -> filter.remoteClaims(new PlainJWT(claims).serialize()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void setsAuthenticationAndCleansActorWhenDownstreamFails() throws Exception {
        ActorContext context = new ActorContext();
        JwtTokenConfig config = new JwtTokenConfig();
        ResourceServerConfig resourceServerConfig = new ResourceServerConfig();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(resourceServerConfig, null, context,
                claims -> Actor.builder().actorId("user-1").role("ADMIN").build(),
                actor -> List.of(new SimpleGrantedAuthority("READ"))) {
            @Override
            public Map<String, Object> remoteClaims(String token) {
                assertEquals("token-with-Bearer-inside", token);
                return Map.of("sub", "user-1");
            }
        };
        Map<String, String> responseHeaders = new HashMap<>();
        try {
            assertThrows(ServletException.class, () -> filter.doFilterInternal(
                    request(Map.of("Authorization", "bearer token-with-Bearer-inside", X_TRACE_ID, "trace-1"), null),
                    response(responseHeaders), (request, response) -> {
                        assertEquals("trace-1", responseHeaders.get(X_TRACE_ID));
                        assertEquals("user-1", context.getActor().getActorId());
                        assertEquals("trace-1", context.getActor().getTraceId());
                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        assertEquals("user-1", authentication.getPrincipal());
                        assertEquals(List.of(new SimpleGrantedAuthority("READ")), List.copyOf(authentication.getAuthorities()));
                        throw new ServletException("downstream failure");
                    }));
            assertNull(context.getActor());
        } finally {
            context.resetActor();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void failedWebSocketTokenContinuesWithoutAuthenticationAndCleansContext() throws Exception {
        ActorContext context = new ActorContext();
        AtomicInteger calls = new AtomicInteger();
        ResourceServerConfig resourceServerConfig = new ResourceServerConfig();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(resourceServerConfig, null, context, null, null) {
            @Override
            public Map<String, Object> remoteClaims(String token) throws IOException {
                calls.incrementAndGet();
                assertEquals("ws-token", token);
                throw new IOException("invalid token");
            }
        };
        Map<String, String> responseHeaders = new HashMap<>();
        try {
            filter.doFilterInternal(request(Map.of("Upgrade", "WebSocket", "Connection", "keep-alive, Upgrade"), "ws-token"),
                    response(responseHeaders), (request, response) -> {
                        assertNull(SecurityContextHolder.getContext().getAuthentication());
                        assertNotNull(context.getActor().getTraceId());
                        assertEquals(context.getActor().getTraceId(), responseHeaders.get(X_TRACE_ID));
                    });
            assertEquals(1, calls.get());
            assertNull(context.getActor());
        } finally {
            context.resetActor();
            SecurityContextHolder.clearContext();
        }
    }

    private HttpServletRequest request(Map<String, String> headers, String token) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        return headers.get(args[0]);
                    }
                    if ("getParameter".equals(method.getName()) && "token".equals(args[0])) {
                        return token;
                    }
                    return null;
                });
    }

    private HttpServletResponse response(Map<String, String> headers) {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName())) {
                        headers.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
    }

    private String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
