package com.trionesdev.spring.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trionesdev.spring.security.ResourceServerConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Proxy;
import java.util.Map;

class JwtAuthenticationFilterTest {


    private static ResourceServerConfig remoteConfig(String jwkSetUri) {
        ResourceServerConfig config = new ResourceServerConfig();
        config.setEnabled(true);
        config.getJwt().setJwkSetUri(jwkSetUri);
        return config;
    }

    private HttpServletRequest request(Map<String, String> headers, String token) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        return headers.get(args[0]);
                    }
                    if ("getParameter".equals(method.getName()) && "token".equals(args[0])) {
                        return token;
                    }
                    return null;
                });
    }

    private HttpServletResponse response(Map<String, String> headers) {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName())) {
                        headers.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
    }

    private String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
