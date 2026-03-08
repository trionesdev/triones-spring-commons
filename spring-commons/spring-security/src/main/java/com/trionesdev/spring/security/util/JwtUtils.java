package com.trionesdev.spring.security.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.SneakyThrows;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    @SneakyThrows
    public static String serialize(String subject, String secret, Map<String, Object> claims, int expires) {
        Date issueAt = new Date();

        JWTClaimsSet.Builder jwtClaimsSetBuilder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(issueAt);
        if (MapUtils.isNotEmpty(claims)) {
            claims.forEach(jwtClaimsSetBuilder::claim);
        }
        if (expires > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(issueAt);
            calendar.add(Calendar.SECOND, expires);
            jwtClaimsSetBuilder.expirationTime(calendar.getTime());
        }
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), jwtClaimsSetBuilder.build());
        signedJWT.sign(new MACSigner(StringUtils.rightPad(secret, 128, '0')));
        return signedJWT.serialize();
    }

    @SneakyThrows
    public static Map<String, Object> parse(String token, String secret) {
        JWSVerifier verifier = new MACVerifier(StringUtils.rightPad(secret, 128, '0'));
        SignedJWT signedJWT = SignedJWT.parse(token);
        if (!signedJWT.verify(verifier)) {
            return null;
        }
        return signedJWT.getJWTClaimsSet().getClaims();
    }
}
