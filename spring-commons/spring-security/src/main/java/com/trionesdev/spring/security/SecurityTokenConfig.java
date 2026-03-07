package com.trionesdev.spring.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityTokenConfig {
    @Builder.Default
    private String headerKey = AUTHORIZATION;
    @Builder.Default
    private String queryParamKey = "token";
    @Builder.Default
    private int expires = 86400;
    @Builder.Default
    private int refreshExpires = 2592000;
    @Builder.Default
    private Jwt jwt = new Jwt();

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Jwt{
        @Builder.Default
        private Boolean enabled = false;
        @Builder.Default
        private String secret = "trionesdev_secret";
    }
}
