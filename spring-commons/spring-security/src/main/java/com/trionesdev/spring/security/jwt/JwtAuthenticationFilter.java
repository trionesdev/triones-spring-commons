package com.trionesdev.spring.security.jwt;

import com.alibaba.fastjson2.JSON;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtTokenFacade;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.DefaultActorAuthoritiesResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.trionesdev.commons.context.actor.ActorConstants.X_TRACE_ID;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenConfig jwtTokenConfig;
    private final JwtTokenFacade jwtFacade;
    private final ActorContext actorContext;
    private final JwtActorResolver jwtActorResolver;
    private final ActorAuthoritiesResolver actorAuthoritiesResolver;
    private volatile JWTProcessor<SecurityContext> remoteJwtProcessor;

    public JwtAuthenticationFilter(JwtTokenConfig jwtTokenConfig, JwtTokenFacade jwtFacade,
                                   ActorContext actorContext, JwtActorResolver jwtActorResolver,
                                   ActorAuthoritiesResolver actorAuthoritiesResolver) {
        this.jwtTokenConfig = Objects.requireNonNull(jwtTokenConfig, "jwtTokenConfig");
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
                    // Preserve the existing local claims endpoint behavior.
                    if (BooleanUtils.isFalse(jwtTokenConfig.getRemote())
                            && Objects.equals(request.getRequestURI(),jwtTokenConfig.getIssuerUri())) {
                        response.setContentType(APPLICATION_JSON_VALUE);
                        response.getWriter().write(JSON.toJSONString(jwtFacade.parse(token)));
                        return;
                    }
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

    private void authenticate(String token, String traceId) throws IOException, ParseException, JOSEException {
        Map<String, Object> claims = BooleanUtils.isTrue(jwtTokenConfig.getRemote())
                ? remoteClaims(token) : jwtFacade.parse(token).getClaims();
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
                .tenantMemberId(actor.getTenantMemberId())
                .attributes(actor.getAttributes())
                .build();
    }

    /**
     * Fetches the remote JWKS and verifies the token before returning its claims.
     */
    public Map<String, Object> remoteClaims(String token) throws IOException {
        try {
            return getRemoteJwtProcessor().process(token, null).getClaims();
        } catch (ParseException | BadJOSEException | JOSEException ex) {
            throw new IOException("Failed to verify remote JWT", ex);
        }
    }

    private JWTProcessor<SecurityContext> getRemoteJwtProcessor() throws IOException {
        if (remoteJwtProcessor == null) {
            synchronized (this) {
                if (remoteJwtProcessor == null) {
                    JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                            .<SecurityContext>create(new URL(jwtTokenConfig.getIssuerUri()))
                            .build();
                    Set<JWSAlgorithm> algorithms = new HashSet<>(JWSAlgorithm.Family.RSA);
                    algorithms.addAll(JWSAlgorithm.Family.EC);
                    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
                    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, jwkSource));
                    remoteJwtProcessor = processor;
                }
            }
        }
        return remoteJwtProcessor;
    }
}
