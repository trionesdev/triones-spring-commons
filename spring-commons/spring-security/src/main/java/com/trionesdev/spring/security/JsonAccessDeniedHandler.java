package com.trionesdev.spring.security;

import com.alibaba.fastjson2.JSON;
import com.trionesdev.commons.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@Slf4j
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (log.isWarnEnabled()) {
            log.warn(accessDeniedException.getMessage());
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        ErrorResponse errorResponse = ErrorResponse.builder().code(HttpStatus.FORBIDDEN.name()).message("未授权").build();
        response.setContentType(APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(errorResponse));
    }
}
