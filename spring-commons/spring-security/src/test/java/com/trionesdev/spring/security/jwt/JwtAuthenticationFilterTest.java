package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorConstants;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.security.auth.client.SecurityContextUserTokenResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    private final JwtFacade jwtFacade = new JwtFacade(JwtConfig.builder()
            .algorithm(JWSAlgorithm.HS256).secret("0123456789abcdef0123456789abcdef").expiration(1800).build());
    private final ActorContext actorContext = new ActorContext();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtFacade, actorContext, null, null);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        request.addHeader("User-Agent", "junit");
        request.addHeader(ActorConstants.X_TRACE_ID, "trace-1");
        return request;
    }

    @Test
    void authenticatesAndExposesTokenForRelay() throws Exception {
        String token = jwtFacade.generate("u1", Map.of(ACTOR_ID, "a1", ACTOR_USER_ID, "u1", ACTOR_ROLE, "USER",
                ACTOR_TENANT_ID, "t1", ACTOR_MEMBER_ID, "m1"));
        AtomicReference<Actor> actorInChain = new AtomicReference<>();
        AtomicReference<String> relayedToken = new AtomicReference<>();
        AtomicReference<JwtUserDetails> details = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(token), response, (req, res) -> {
            actorInChain.set(actorContext.getActor());
            relayedToken.set(new SecurityContextUserTokenResolver().resolve());
            details.set((JwtUserDetails) SecurityContextHolder.getContext().getAuthentication().getCredentials());
        });

        assertEquals(token, relayedToken.get());
        assertEquals("m1", details.get().getMemberId());
        assertEquals("t1", details.get().getTenantId());
        Actor actor = actorInChain.get();
        assertEquals("a1", actor.getActorId());
        assertEquals("m1", actor.getMemberId());
        assertEquals("trace-1", actor.getTraceId());
        assertEquals("10.0.0.1", actor.getIpAddress());
        assertEquals("junit", actor.getUserAgent());
        assertNotNull(actor.getTime());
        assertEquals("trace-1", response.getHeader(ActorConstants.X_TRACE_ID));
    }

    @Test
    void anonymousRequestStillHasRequestInfo() throws Exception {
        AtomicReference<Actor> actorInChain = new AtomicReference<>();
        filter.doFilter(request(null), new MockHttpServletResponse(),
                (req, res) -> actorInChain.set(actorContext.getActor()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(actorInChain.get().getActorId());
        assertEquals("10.0.0.1", actorInChain.get().getIpAddress());
        assertEquals("junit", actorInChain.get().getUserAgent());
    }

    @Test
    void invalidTokenContinuesAnonymously() throws Exception {
        AtomicReference<Boolean> authenticated = new AtomicReference<>();
        filter.doFilter(request("not-a-jwt"), new MockHttpServletResponse(),
                (req, res) -> authenticated.set(SecurityContextHolder.getContext().getAuthentication() != null));
        assertFalse(authenticated.get());
    }
}
