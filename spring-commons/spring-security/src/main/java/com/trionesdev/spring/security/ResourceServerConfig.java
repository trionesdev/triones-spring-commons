package com.trionesdev.spring.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ResourceServerConfig {
    private Boolean enabled = false;
    private Jwt jwt = new Jwt();

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Jwt {
        private String issuerUri;
        private String jwkSetUri;
    }
}
