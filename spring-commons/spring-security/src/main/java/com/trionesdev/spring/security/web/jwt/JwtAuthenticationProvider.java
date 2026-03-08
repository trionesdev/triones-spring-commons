package com.trionesdev.spring.security.web.jwt;

import com.trionesdev.spring.security.AbstractAuthenticationProvider;
import com.trionesdev.spring.security.AuthorityManager;
import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.TokenStorage;
import com.trionesdev.spring.security.util.AuthUtils;
import com.trionesdev.spring.security.util.JwtUtils;
import com.trionesdev.spring.security.web.TokenAuthenticationToken;
import com.trionesdev.spring.security.web.TokenUserDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

/**
 * Jwt认证提供者
 */
public class JwtAuthenticationProvider extends AbstractAuthenticationProvider {

    public JwtAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager, TokenStorage tokenStorage) {
        super(config, authorityManager, tokenStorage);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        TokenAuthenticationToken tokenAuthenticationToken = (TokenAuthenticationToken) authentication;
        String token = tokenAuthenticationToken.getToken();
        if (StringUtils.isBlank(token)) {
            return tokenAuthenticationToken;
        }
        try {
            Map<String, Object> claims = JwtUtils.parse(token, config.getSecret());
            if (claims != null) {
                TokenUserDetails userDetails = TokenUserDetails.builder().claims(claims).build();
                tokenAuthenticationToken.setDetails(userDetails);
                List<GrantedAuthority> authorities = AuthUtils.getAuthorities(authorityManager, tokenAuthenticationToken);
                tokenAuthenticationToken.setAuthorities(authorities);
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
