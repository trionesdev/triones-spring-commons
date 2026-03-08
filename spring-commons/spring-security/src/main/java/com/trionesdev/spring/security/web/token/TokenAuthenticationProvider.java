package com.trionesdev.spring.security.web.token;

import com.trionesdev.spring.security.AbstractAuthenticationProvider;
import com.trionesdev.spring.security.AuthorityManager;
import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.TokenType;
import com.trionesdev.spring.security.token.TokenStorage;
import com.trionesdev.spring.security.util.JwtUtils;
import com.trionesdev.spring.security.web.TokenAuthenticationToken;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;
import java.util.Objects;

/**
 * Token认证提供者 (apiKey,bearerToken)
 */
public class TokenAuthenticationProvider extends AbstractAuthenticationProvider {

    public TokenAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager, TokenStorage tokenStorage) {
        super(config, authorityManager, tokenStorage);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        TokenAuthenticationToken tokenAuthenticationToken = (TokenAuthenticationToken) authentication;
        String token = tokenAuthenticationToken.getToken();
        if (StringUtils.isBlank(token)) {
            return tokenAuthenticationToken;
        }
        Map<String, Object> claims;
        if (Objects.equals(TokenType.jwt, config.getTokenType())){
            claims = JwtUtils.parse(token,config.getSecret());
        }else {
            claims = tokenStorage.get(token);
        }
        return tokenAuthenticationToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
