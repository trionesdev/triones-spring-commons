package com.trionesdev.spring.security.auth.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextUserTokenResolverTest {
    private final SecurityContextUserTokenResolver resolver = new SecurityContextUserTokenResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Object credentials) {
        var authentication = new TestingAuthenticationToken("user", credentials, List.of());
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void bearerTokenCredentials() {
        authenticate((BearerTokenCredentials) () -> " user-token ");
        assertEquals("user-token", resolver.resolve());
    }

    @Test
    void oauth2Token() {
        authenticate(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "oauth2-token",
                Instant.now(), Instant.now().plusSeconds(60)));
        assertEquals("oauth2-token", resolver.resolve());
    }

    @Test
    void unsupportedOrMissing() {
        assertNull(resolver.resolve());
        authenticate("password");
        assertNull(resolver.resolve());
        authenticate((BearerTokenCredentials) () -> " ");
        assertNull(resolver.resolve());
    }

    @Test
    void unauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", (BearerTokenCredentials) () -> "t"));
        assertNull(resolver.resolve());
    }
}
