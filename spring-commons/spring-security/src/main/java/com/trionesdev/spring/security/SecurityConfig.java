package com.trionesdev.spring.security;

import lombok.Data;

@Data
public class SecurityConfig {
    private String tokenKey = "token";
    private String secret = "trionesdev_secret";
    private int expires = 86400;
    private int refreshExpires = 2592000;
}
