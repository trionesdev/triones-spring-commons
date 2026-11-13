package com.trionesdev.spring.security;

import com.trionesdev.commons.context.actor.Actor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.UUID;

import static com.trionesdev.commons.context.actor.ActorConstants.X_TRACE_ID;

/**
 * JWT 过滤器与 OAuth2 资源服务器共用的 {@link Actor} 请求信息填充，保证两种模式下 Actor 字段一致。
 */
public final class ActorRequestSupport {
    private ActorRequestSupport() {
    }

    /**
     * 读取请求头 {@code X-Request-TraceId}，没有则生成 UUID，并回写到响应头
     */
    public static String resolveTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = StringUtils.defaultIfBlank(request.getHeader(X_TRACE_ID), UUID.randomUUID().toString());
        response.setHeader(X_TRACE_ID, traceId);
        return traceId;
    }

    /**
     * 写入 traceId、时间、客户端 IP 与 User-Agent
     */
    public static Actor fill(Actor actor, HttpServletRequest request, String traceId) {
        actor.setTraceId(traceId);
        actor.setTime(Instant.now());
        actor.setIpAddress(resolveClientIp(request));
        actor.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        return actor;
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.trim(StringUtils.substringBefore(forwardedFor, ","));
        }
        return StringUtils.defaultIfBlank(request.getHeader("X-Real-IP"), request.getRemoteAddr());
    }
}
