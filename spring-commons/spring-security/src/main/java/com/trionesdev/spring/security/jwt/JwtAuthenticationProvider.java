package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.trionesdev.spring.security.AbstractAuthenticationProvider;
import com.trionesdev.spring.security.AuthorityManager;
import com.trionesdev.spring.security.SecurityTokenConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JwtAuthenticationProvider extends AbstractAuthenticationProvider {

    public JwtAuthenticationProvider(SecurityTokenConfig config, AuthorityManager authorityManager) {
        super(config, authorityManager);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
        String token = jwtAuthenticationToken.getToken();
        if (StringUtils.isBlank(token)) {
            return jwtAuthenticationToken;
        }
        try {
            Map<String, Object> claims = parseJwtToken(token);
            if (claims != null) {
                JwtUserDetails userDetails = JwtUserDetails.builder().claims(claims).build();
                jwtAuthenticationToken.setDetails(userDetails);
                List<GrantedAuthority> authorities = new ArrayList<>();
                if (authorityManager != null) {
                    List<String> roles = authorityManager.getRoles(jwtAuthenticationToken);
                    List<String> permissions = authorityManager.getPermissions(jwtAuthenticationToken);
                    if (CollectionUtils.isNotEmpty(roles)) {
                        String[] roleArray = roles.stream().map(role -> "ROLE_" + role).distinct().toArray(String[]::new);
                        authorities.addAll(AuthorityUtils.createAuthorityList(roleArray));
                    }
                    if (CollectionUtils.isNotEmpty(permissions)) {
                        authorities.addAll(AuthorityUtils.createAuthorityList(permissions.toArray(new String[0])));
                    }
                    jwtAuthenticationToken.setAuthorities(authorities);
                }
                jwtAuthenticationToken.setAuthenticated(true);
//                JwtAuthenticationToken newAuth = new JwtAuthenticationToken(authorities);
//                newAuth.setToken(token);
//                newAuth.setDetails(userDetails);
//                newAuth.setAuthenticated(true);
                return jwtAuthenticationToken;
            }
            return jwtAuthenticationToken;
        } catch (Exception e) {
            return jwtAuthenticationToken;
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
