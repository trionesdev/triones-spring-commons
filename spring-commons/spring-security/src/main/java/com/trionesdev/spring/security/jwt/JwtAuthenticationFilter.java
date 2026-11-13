package com.trionesdev.spring.security.jwt;

import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.spring.security.auth.client.jwks.JwkSetJwtVerifier;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.DefaultActorAuthoritiesResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static com.trionesdev.commons.context.actor.ActorConstants.X_TRACE_ID;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtFacade jwtFacade;
    private final ActorContext actorContext;
    private final JwtActorResolver jwtActorResolver;
    private final ActorAuthoritiesResolver actorAuthoritiesResolver;
    private volatile JwkSetJwtVerifier remoteJwtVerifier;

    public JwtAuthenticationFilter(
                                   JwtFacade jwtFacade,
                                   ActorContext actorContext, JwtActorResolver jwtActorResolver,
                                   ActorAuthoritiesResolver actorAuthoritiesResolver) {


        this.jwtFacade = jwtFacade;
        this.actorContext = Objects.requireNonNull(actorContext, "actorContext");
        this.jwtActorResolver = jwtActorResolver != null ? jwtActorResolver : new DefaultJwtActorResolver();
        this.actorAuthoritiesResolver = actorAuthoritiesResolver != null
                ? actorAuthoritiesResolver : new DefaultActorAuthoritiesResolver();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(X_TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        response.setHeader(X_TRACE_ID, traceId);

        Actor actor = new Actor();
        actor.setTraceId(traceId);
        actor.setTime(Instant.now());
        try {
            actorContext.setActor(actor);
            String token = resolveToken(request);
            if (StringUtils.isNotBlank(token)) {
                try {
                    authenticate(token, traceId);
                } catch (Exception ex) {
                    // Continue anonymously; authorization remains the security chain's responsibility.
                    SecurityContextHolder.clearContext();
                    actorContext.setActor(actor);
                    log.warn("JWT authentication failed", ex);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            actorContext.resetActor();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = StringUtils.trimToNull(request.getHeader(AUTHORIZATION));
        if (authorization != null) {
            if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                authorization = StringUtils.trimToNull(authorization.substring(BEARER_PREFIX.length()));
            } else if ("Bearer".equalsIgnoreCase(authorization)) {
                authorization = null;
            }
        }
        // Retain support for raw token headers and WebSocket query tokens.
        if (authorization == null && isWebSocketRequest(request)) {
            return StringUtils.trimToNull(request.getParameter("token"));
        }
        return authorization;
    }

    private boolean isWebSocketRequest(HttpServletRequest request) {
        if (!"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return false;
        }
        String connection = request.getHeader("Connection");
        if (connection != null) {
            for (String value : connection.split(",")) {
                if ("upgrade".equalsIgnoreCase(value.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Authenticates the request JWT and sets the security context.
     * 启用 Resource Server 则进行远程解析，否则本地解析
     * @param token
     * @param traceId
     * @throws IOException
     */
    private void authenticate(String token, String traceId) throws IOException {
        Map<String, Object> claims =   jwtFacade.parse(token).getClaims();
        if (claims == null) {
            return;
        }
        Actor actor = jwtActorResolver.resolve(claims);
        if (actor == null) {
            return;
        }
        actor.setTraceId(traceId);
        actor.setTime(Instant.now());
        Collection<? extends GrantedAuthority> authorities = actorAuthoritiesResolver.resolve(actor);
        JwtUserDetails userDetails = createUserDetails(token, actor);
        Authentication authentication =
                new JwtAuthenticationToken(userDetails.getActorId(), userDetails, authorities);
        actorContext.setActor(actor);
        // Use a fresh context instead of mutating a possibly shared session context.
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private JwtUserDetails createUserDetails(String token, Actor actor) {
        return JwtUserDetails.builder()
                .token(token)
                .actorId(actor.getActorId())
                .userId(actor.getUserId())
                .role(actor.getRole())
                .tenantId(actor.getTenantId())
                .attributes(actor.getAttributes())
                .build();
    }

    /**
     * 通过 jwk-set-uri 获取授权服务器公钥（{@link JwkSetJwtVerifier}，带缓存）并校验 token。
     */

}
