package com.trionesdev.spring.security.util;

import com.trionesdev.spring.security.AuthorityManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.List;

public class AuthUtils {
    public static List<GrantedAuthority> getAuthorities(AuthorityManager authorityManager, Authentication authentication) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (authorityManager != null) {
            List<String> roles = authorityManager.getRoles(authentication);
            List<String> permissions = authorityManager.getPermissions(authentication);
            if (CollectionUtils.isNotEmpty(roles)) {
                String[] roleArray = roles.stream().map(role -> "ROLE_" + role).distinct().toArray(String[]::new);
                authorities.addAll(org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(roleArray));
            }
            if (CollectionUtils.isNotEmpty(permissions)) {
                authorities.addAll(org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(permissions.toArray(new String[0])));
            }
        }
        return authorities;
    }
}
