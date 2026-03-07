package com.trionesdev.spring.security.jwt;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
public class JwtUserDetails {
    private String subject;
    private Map<String, Object> claims;
}
