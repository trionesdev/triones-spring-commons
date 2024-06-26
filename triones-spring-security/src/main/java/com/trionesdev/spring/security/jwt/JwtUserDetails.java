package com.trionesdev.spring.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String tenantMemberId;
}
