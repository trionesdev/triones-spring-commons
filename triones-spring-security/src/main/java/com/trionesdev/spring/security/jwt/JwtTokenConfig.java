package com.trionesdev.spring.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtTokenConfig {
    private Boolean local;
    private String protocol;
    private String endpoint;
    private String secret;
    private int expiration;
}
