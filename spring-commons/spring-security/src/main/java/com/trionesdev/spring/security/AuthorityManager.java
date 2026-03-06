package com.trionesdev.spring.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public interface AuthorityManager {
    List<GrantedAuthority> getAuthorities(Authentication authentication);
}
