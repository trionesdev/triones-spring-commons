package com.trionesdev.spring.security.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 *
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityToken {
    private String accessToken;
    private String refreshToken;
    private long expiresAt;
    private long refreshExpiresAt;
}
