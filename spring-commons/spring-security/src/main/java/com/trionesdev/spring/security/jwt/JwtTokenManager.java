package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trionesdev.spring.security.SecurityTokenConfig;
import com.trionesdev.spring.security.token.Token;
import com.trionesdev.spring.security.token.TokenDefinition;
import com.trionesdev.spring.security.token.TokenManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;
import java.util.Date;

@RequiredArgsConstructor
public class JwtTokenManager implements TokenManager {
    private final SecurityTokenConfig config;
    @Override
    public Token createToken(TokenDefinition tokenDefinition) {
        Date issueAt = new Date();

        JWTClaimsSet.Builder jwtClaimsSetBuilder = new JWTClaimsSet.Builder()
                .subject(tokenDefinition.getSubject())
                .issueTime(issueAt);
        if (MapUtils.isNotEmpty(tokenDefinition.getClaims())) {
            tokenDefinition.getClaims().forEach(jwtClaimsSetBuilder::claim);
        }
        if (config.getExpires() > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(issueAt);
            calendar.add(Calendar.SECOND, config.getExpires());
            jwtClaimsSetBuilder.expirationTime(calendar.getTime());
        }
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), jwtClaimsSetBuilder.build());
        try {
            signedJWT.sign(new MACSigner(StringUtils.rightPad(config.getSecret(), 128, '0')));
            String token = signedJWT.serialize();
            return Token.builder()
                    .accessToken(token)
                    .build();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Token logout(String token) {
        return null;
    }
}
