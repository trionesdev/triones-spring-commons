package com.trionesdev.spring.security.jwt;

import com.trionesdev.spring.security.auth.client.BearerTokenCredentials;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
/**
 * JwtAuthenticationToken 的 credentials；实现 {@link BearerTokenCredentials}，供 spring-auth-client 向下游透传用户 token。
 */
public class JwtUserDetails implements BearerTokenCredentials {
    private String token;
    private String actorId;
    private String userId;
    private String role;
    private String tenantId;
    private String tenantMemberId;
    private Map<String,Object> attributes;
}
