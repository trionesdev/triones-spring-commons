package com.trionesdev.spring.security.util;

import com.trionesdev.spring.security.SecurityTokenConfig;

import java.util.UUID;

public class TokenUtils {
    public static String generateToken(SecurityTokenConfig config) {
        switch (config.getTokenStyle()) {
            case uuid -> {
                return UUID.randomUUID().toString();
            }
            case simpleUuid -> {
                return UUID.randomUUID().toString().replaceAll("-", "");
            }
            default -> {
                return UUID.randomUUID().toString();
            }
        }
    }
}
