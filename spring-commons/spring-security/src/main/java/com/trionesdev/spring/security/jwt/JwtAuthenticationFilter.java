package com.trionesdev.spring.security.jwt;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.common.collect.Lists;
import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtFacade;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.trionesdev.commons.context.actor.ActorConstants.X_TRACE_ID;
import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenConfig jwtTokenConfig;
    private final JwtFacade jwtFacade;
    private final ActorContext actorContext;

    private final String JWT_TOKEN_URI = "jwt/token";

    public JwtAuthenticationFilter(JwtTokenConfig jwtTokenConfig, JwtFacade jwtFacade, ActorContext actorContext) {
        this.jwtTokenConfig = jwtTokenConfig;
        this.jwtFacade = jwtFacade;
        this.actorContext = actorContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Actor actor = new Actor();
        try {
            String traceId = request.getHeader(X_TRACE_ID);
            if (StringUtils.isNotBlank(traceId)) {
                actor.setTraceId(traceId);
            }else {
                actor.setTraceId(UUID.randomUUID().toString());
            }
            String authorization = request.getHeader(AUTHORIZATION);
            if (StringUtils.isNotBlank(authorization)) {
                authorization = authorization.replace("Bearer", "").trim();
            }
            //region jwt token 解析逻辑
            if (BooleanUtils.isFalse(jwtTokenConfig.getRemote()) && StringUtils.isNotBlank(authorization)) {
                Pattern pattern = Pattern.compile(JWT_TOKEN_URI);
                Matcher matcher = pattern.matcher(request.getRequestURI());
                if (matcher.matches()) {
                    response.setContentType(APPLICATION_JSON_VALUE);
                    response.getWriter().write(JSON.toJSONString(jwtFacade.parse(authorization)));
                    return;
                }
            }
            //endregion


            if (StringUtils.isNotBlank(authorization)) {

                Map<String, Object> claims = null;
                if (BooleanUtils.isFalse(jwtTokenConfig.getRemote())) {
                    claims = jwtFacade.parse(authorization);
                } else {
                    claims = remoteClaims(authorization);
                }
                if (Objects.nonNull(claims)) {
                    String actorId = Optional.ofNullable(claims.get(ACTOR_ID)).map(String::valueOf).orElse(null);
                    String userId = Optional.ofNullable(claims.get(ACTOR_USER_ID)).map(String::valueOf).orElse(null);
                    String role = Optional.ofNullable(claims.get(ACTOR_ROLE)).map(String::valueOf).orElse(null);
                    String tenantId = Optional.ofNullable(claims.get(ACTOR_TENANT_ID)).map(String::valueOf).orElse(null);
                    String memberId = Optional.ofNullable(claims.get(ACTOR_MEMBER_ID)).map(String::valueOf).orElse(null);
                    String tenantMemberId = Optional.ofNullable(claims.get(ACTOR_TENANT_MEMBER_ID)).map(String::valueOf).orElse(null);
                    Map<String, Object> attributes = Optional.ofNullable(claims.get(ACTOR_ATTRIBUTES))
                            .map(o -> JSON.parseObject(JSON.toJSONString(o), new TypeReference<Map<String, Object>>() {
                            })).orElse(null);
                    if ((Objects.nonNull(actorId) || Objects.nonNull(userId)) && Objects.nonNull(role)) {
                        actor.setActorId(actorId);
                        actor.setUserId(userId);
                        actor.setRole(role);
                        actor.setTenantId(tenantId);
                        actor.setMemberId(memberId);
                        actor.setTenantMemberId(tenantMemberId);
                        actor.setAttributes(attributes);
                        actor.setTime(Instant.now());
                        JwtUserDetails userDetails =
                                JwtUserDetails.builder().token(authorization)
                                        .actorId(actor.getActorId())
                                        .userId(actor.getUserId())
                                        .role(actor.getRole())
                                        .tenantId(actor.getTenantId())
                                        .tenantMemberId(actor.getTenantMemberId())
                                        .attributes(attributes)
                                        .build();
                        List<SimpleGrantedAuthority> authorities = Lists.newArrayList(new SimpleGrantedAuthority(role));
                        Authentication authentication =
                                new JwtAuthenticationToken(userDetails.getActorId(), userDetails, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }

            }
            actorContext.setActor(actor);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        filterChain.doFilter(request, response);
        response.setHeader(X_TRACE_ID, actor.getTraceId());
        actorContext.resetActor();
    }


    public Map<String, Object> remoteClaims(String token) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(jwtTokenConfig.getEndpoint() + "/" + JWT_TOKEN_URI)
                .header(AUTHORIZATION, token)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return JSON.parseObject(response.body().string(), new TypeReference<Map<String, Object>>() {
            });
        }
    }
}
