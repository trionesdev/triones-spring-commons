package com.trionesdev.spring.security.jwt1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtTokenConfig {
    private Boolean remote = false;
    private String protocol;
    private String endpoint;
    private String secret;
    private int expiration;
}
