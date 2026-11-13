package com.trionesdev.spring.security.oauth2;

import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.spring.security.DefaultActorAuthoritiesResolver;
import com.trionesdev.spring.security.auth.client.SecurityContextUserTokenResolver;
import com.trionesdev.spring.security.jwt.DefaultJwtActorResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorJwtAuthenticationTest {
    private final ActorJwtAuthenticationConverter converter =
            new ActorJwtAuthenticationConverter(new DefaultJwtActorResolver(), new DefaultActorAuthoritiesResolver());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("raw-token").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void platformToken() {
        var token = (ActorJwtAuthenticationToken) converter.convert(jwt().subject("u1")
                .claim(ACTOR_ID, "a1").claim(ACTOR_MEMBER_ID, "m1").claim("client_id", "web")
                .claim("scope", "order.read").build());

        Actor actor = token.getActor();
        assertEquals("a1", actor.getActorId());
        assertEquals("m1", actor.getMemberId());
        assertEquals("web", actor.getAttributes().get(ActorJwtAuthenticationConverter.ATTRIBUTE_CLIENT_ID));
        assertEquals("a1", token.getName());
        assertTrue(token.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch("SCOPE_order.read"::equals));
    }

    @Test
    void clientCredentialsTokenUsesSubject() {
        var token = (ActorJwtAuthenticationToken) converter.convert(jwt().subject("order-service").build());
        assertEquals("order-service", token.getActor().getActorId());
    }

    @Test
    void contextFilterFillsActorAndTokenCanBeRelayed() throws Exception {
        var authentication = converter.convert(jwt().subject("u1").claim(ACTOR_ID, "a1").build());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        ActorContext actorContext = new ActorContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Real-IP", "10.0.0.9");
        request.addHeader("User-Agent", "junit");
        AtomicReference<Actor> actorInChain = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ActorContextFilter(actorContext).doFilter(request, response,
                (req, res) -> actorInChain.set(actorContext.getActor()));

        assertEquals("a1", actorInChain.get().getActorId());
        assertEquals("10.0.0.9", actorInChain.get().getIpAddress());
        assertEquals("junit", actorInChain.get().getUserAgent());
        assertNotNull(response.getHeader(ActorContextFilter.TRACE_ID_HEADER));
        assertEquals("raw-token", new SecurityContextUserTokenResolver().resolve());
    }
}
