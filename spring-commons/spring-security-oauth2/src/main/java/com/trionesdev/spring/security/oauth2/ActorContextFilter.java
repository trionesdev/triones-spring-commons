package com.trionesdev.spring.security.oauth2;

import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * 把认证结果中的 {@link Actor} 写入 {@link ActorContext}，请求结束后清理。
 * 放在 BearerTokenAuthenticationFilter 之后；未认证的请求（放行接口）写入只有 traceId / 时间 / 客户端信息的空 Actor。
 */
@RequiredArgsConstructor
public class ActorContextFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_HEADER = "X-Request-TraceId";

    private final ActorContext actorContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var traceId = StringUtils.defaultIfBlank(request.getHeader(TRACE_ID_HEADER), UUID.randomUUID().toString());
        response.setHeader(TRACE_ID_HEADER, traceId);

        var actor = SecurityContextHolder.getContext().getAuthentication() instanceof ActorJwtAuthenticationToken token
                ? token.getActor()
                : new Actor();
        actor.setTraceId(traceId);
        actor.setTime(Instant.now());
        actor.setIpAddress(resolveClientIp(request));
        actor.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));

        actorContext.setActor(actor);
        try {
            filterChain.doFilter(request, response);
        } finally {
            actorContext.resetActor();
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        var forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.trim(StringUtils.substringBefore(forwardedFor, ","));
        }
        return StringUtils.defaultIfBlank(request.getHeader("X-Real-IP"), request.getRemoteAddr());
    }
}
