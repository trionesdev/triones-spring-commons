package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.trionesdev.spring.security.AbstractAuthenticationProvider;
import com.trionesdev.spring.security.SecurityConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.text.ParseException;
import java.util.Map;

public class JwtAuthenticationProvider extends AbstractAuthenticationProvider {

    public JwtAuthenticationProvider(SecurityConfig config) {
        super(config);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
        try {
            Map<String, Object> claims = parseJwtToken(jwtAuthenticationToken.getToken());
            if (claims == null) {
                return null;
            }else {
                jwtAuthenticationToken.setClaims(claims);
                jwtAuthenticationToken.setAuthenticated(true);
                return jwtAuthenticationToken;
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Map<String, Object> parseJwtToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(StringUtils.rightPad(config.getSecret(), 128, '0'));
        SignedJWT signedJWT = SignedJWT.parse(token);
        if (!signedJWT.verify(verifier)) {
            return null;
        }
        return signedJWT.getJWTClaimsSet().getClaims();
    }

}
