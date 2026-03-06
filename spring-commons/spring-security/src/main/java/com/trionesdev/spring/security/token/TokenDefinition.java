package com.trionesdev.spring.security.token;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
public class TokenDefinition {
    private String subject;
    private String role;
    private String endType;
    private Map<String, Object> claims;
}
