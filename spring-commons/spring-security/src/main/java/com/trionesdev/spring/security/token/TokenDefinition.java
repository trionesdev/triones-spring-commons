package com.trionesdev.spring.security.token;

import lombok.Data;

import java.util.Map;

@Data
public class TokenDefinition {
    private String id;
    private String role;
    private String endType;
    private Map<String, Object> claims;
}
