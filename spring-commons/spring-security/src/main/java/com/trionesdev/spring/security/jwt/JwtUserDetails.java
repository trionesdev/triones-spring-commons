package com.trionesdev.spring.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * {@link JwtAuthenticationToken} 的 credentials：原始 token 与 Actor 信息，可用 {@link #getToken()} 向下游透传用户 token。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtUserDetails {
    private String token;
    private String actorId;
    private String userId;
    private String role;
    private String tenantId;
    private String memberId;
    /**
     * @deprecated 使用 {@link #memberId}
     */
    @Deprecated
    private String tenantMemberId;
    private Map<String, Object> attributes;
}
