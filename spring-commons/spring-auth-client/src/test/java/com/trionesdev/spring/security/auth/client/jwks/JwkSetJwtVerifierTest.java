package com.trionesdev.spring.security.auth.client.jwks;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.trionesdev.spring.security.auth.client.jwks.JwkSetJwtVerifier;
import com.trionesdev.spring.security.auth.client.jwks.JwtVerificationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwkSetJwtVerifierTest {
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<JWKSet> published = new AtomicReference<>();
    private String jwkSetUri;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = published.get().toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        jwkSetUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/jwks";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static JWTClaimsSet claims(int expiresInSeconds) {
        return new JWTClaimsSet.Builder().subject("user-1")
                .expirationTime(Date.from(Instant.now().plusSeconds(expiresInSeconds))).build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    void fetchesRemoteJwksOnceAndVerifies() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
        published.set(new JWKSet(key.toPublicJWK()));
        JwkSetJwtVerifier verifier = JwkSetJwtVerifier.withJwkSetUri(jwkSetUri);

        String token = sign(key, claims(300));
        assertEquals("user-1", verifier.verify(token).get("sub"));
        assertEquals("user-1", verifier.verifyClaimsSet(token).getSubject());
        assertEquals(1, requests.get(), "JWKS should be cached");
    }

    @Test
    void rejectsWrongKeyExpiredAndUnsignedTokens() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
        published.set(new JWKSet(key.toPublicJWK()));
        JwkSetJwtVerifier verifier = JwkSetJwtVerifier.withJwkSetUri(jwkSetUri);

        RSAKey forged = new RSAKeyGenerator(2048).keyID("k1").generate();
        assertThrows(JwtVerificationException.class, () -> verifier.verify(sign(forged, claims(300))));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(sign(key, claims(-300))));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(new PlainJWT(claims(300)).serialize()));
        assertThrows(JwtVerificationException.class, () -> verifier.verify("not-a-jwt"));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(" "));
    }

    @Test
    void refetchesWhenAuthorizationServerRotatesKey() throws Exception {
        RSAKey oldKey = new RSAKeyGenerator(2048).keyID("old").generate();
        published.set(new JWKSet(oldKey.toPublicJWK()));
        JwkSetJwtVerifier verifier = JwkSetJwtVerifier.withJwkSetUri(jwkSetUri);
        verifier.verify(sign(oldKey, claims(300)));

        RSAKey newKey = new RSAKeyGenerator(2048).keyID("new").generate();
        published.set(new JWKSet(java.util.List.of(newKey.toPublicJWK(), oldKey.toPublicJWK())));
        assertEquals("user-1", verifier.verify(sign(newKey, claims(300))).get("sub"));
        assertEquals("user-1", verifier.verify(sign(oldKey, claims(300))).get("sub"));
        assertEquals(2, requests.get(), "unknown kid should trigger exactly one refetch");
    }

    @Test
    void verifiesEcAndRestrictsAlgorithms() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec").generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("ec").build(), claims(300));
        jwt.sign(new ECDSASigner(ecKey));
        String token = jwt.serialize();

        var source = new ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(new JWKSet(ecKey.toPublicJWK()));
        assertEquals("user-1", new JwkSetJwtVerifier(source).verify(token).get("sub"));
        JwkSetJwtVerifier rsaOnly = new JwkSetJwtVerifier(source, Set.of(JWSAlgorithm.RS256));
        assertThrows(JwtVerificationException.class, () -> rsaOnly.verify(token));
    }

    @Test
    void invalidJwkSetUri() {
        assertThrows(IllegalArgumentException.class, () -> JwkSetJwtVerifier.withJwkSetUri(null));
        assertThrows(IllegalArgumentException.class, () -> JwkSetJwtVerifier.withJwkSetUri("not a url"));
    }
}
