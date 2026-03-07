package com.trionesdev.spring.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityTokenConfig {
    @Builder.Default
    private String tokenKey = "token";
    @Builder.Default
    private String secret = "trionesdev_secret";
    @Builder.Default
    private int expires = 86400;
    @Builder.Default
    private int refreshExpires = 2592000;
}
