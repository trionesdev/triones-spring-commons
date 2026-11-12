package com.trionesdev.spring.security;

import lombok.Data;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

@Data
public class SecurityHttpAuthorizeRule {
    private AuthorizeAction action = AuthorizeAction.AUTHENTICATED;
    private String[] matchers;
    private HttpMethod method;
    private List<String> roles = new ArrayList<>();
    private List<String> authorities = new ArrayList<>();

    public enum AuthorizeAction {
        AUTHENTICATED, ANONYMOUS, PERMIT_ALL, DENY_ALL, HAS_ROLE, HAS_AUTHORITY, HAS_ANY_ROLE, HAS_ANY_AUTHORITY
    }
}
