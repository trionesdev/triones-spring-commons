package com.trionesdev.spring.security.web;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
public class TokenUserDetails {
    private String subject;
    private Map<String, Object> claims;
}
