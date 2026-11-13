package com.trionesdev.spring.security.oauth2;

import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.commons.context.actor.ActorConstants;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.spring.security.ActorRequestSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把认证结果中的 {@link Actor} 写入 {@link ActorContext}，请求结束后清理。
 * 放在 BearerTokenAuthenticationFilter 之后；未认证的请求（放行接口）写入只有 traceId / 时间 / 客户端信息的空 Actor。
 */
@RequiredArgsConstructor
public class ActorContextFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_HEADER = ActorConstants.X_TRACE_ID;

    private final ActorContext actorContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var traceId = ActorRequestSupport.resolveTraceId(request, response);

        var actor = SecurityContextHolder.getContext().getAuthentication() instanceof ActorJwtAuthenticationToken token
                ? token.getActor()
                : new Actor();
        ActorRequestSupport.fill(actor, request, traceId);

        actorContext.setActor(actor);
        try {
            filterChain.doFilter(request, response);
        } finally {
            actorContext.resetActor();
        }
    }
}
