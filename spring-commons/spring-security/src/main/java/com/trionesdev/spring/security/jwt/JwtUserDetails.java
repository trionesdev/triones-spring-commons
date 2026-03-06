package com.trionesdev.spring.security.jwt;

import java.util.Map;

public class JwtUserDetails {
    private String subject;
    private Map<String, Object> claims;
}
