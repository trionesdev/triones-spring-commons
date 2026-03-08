package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AbstractAuthenticationProvider;
import com.trionesdev.spring.security.AuthorityManager;
import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.util.JwtUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TokenAuthenticationProvider extends AbstractAuthenticationProvider {

    public TokenAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager) {
        super(config, authorityManager);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        TokenAuthenticationToken tokenAuthenticationToken = (TokenAuthenticationToken) authentication;
        String token = tokenAuthenticationToken.getToken();
        if (StringUtils.isBlank(token)) {
            return tokenAuthenticationToken;
        }
        try {
            Map<String, Object> claims = JwtUtils.parse(token, config.getJwt().getSecret());
            if (claims != null) {

                TokenUserDetails userDetails = TokenUserDetails.builder().claims(claims).build();
                tokenAuthenticationToken.setDetails(userDetails);
                List<GrantedAuthority> authorities = new ArrayList<>();
                if (authorityManager != null) {
                    List<String> roles = authorityManager.getRoles(tokenAuthenticationToken);
                    List<String> permissions = authorityManager.getPermissions(tokenAuthenticationToken);
                    if (CollectionUtils.isNotEmpty(roles)) {
                        String[] roleArray = roles.stream().map(role -> "ROLE_" + role).distinct().toArray(String[]::new);
                        authorities.addAll(AuthorityUtils.createAuthorityList(roleArray));
                    }
                    if (CollectionUtils.isNotEmpty(permissions)) {
                        authorities.addAll(AuthorityUtils.createAuthorityList(permissions.toArray(new String[0])));
                    }
                    tokenAuthenticationToken.setAuthorities(authorities);
                }
                tokenAuthenticationToken.setAuthenticated(true);
                return tokenAuthenticationToken;
            }
            return tokenAuthenticationToken;
        } catch (Exception e) {
            return tokenAuthenticationToken;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
