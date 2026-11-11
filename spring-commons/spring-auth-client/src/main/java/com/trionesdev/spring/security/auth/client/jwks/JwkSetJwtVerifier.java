package com.trionesdev.spring.security.auth.client.jwks;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 从授权服务器的 JWKS 端点（jwk-set-uri）获取公钥并校验 JWT。
 * <p>
 * 公钥由 nimbus {@link JWKSourceBuilder} 缓存：首次校验时拉取，之后按缓存策略复用；
 * 遇到未知 kid（授权服务器轮换了密钥）时自动重新拉取。签名、过期时间（exp/nbf）校验失败抛出
 * {@link JwtVerificationException}。
 * <pre>{@code
 * JwkSetJwtVerifier verifier = JwkSetJwtVerifier.withJwkSetUri("https://auth.example.com/auth/jwks");
 * Map<String, Object> claims = verifier.verify(token);
 * }</pre>
 * 线程安全，应在应用内复用同一个实例。
 */
public class JwkSetJwtVerifier {
    private final DefaultJWTProcessor<SecurityContext> processor;

    /**
     * @param jwkSource  公钥来源，例如 {@link JWKSourceBuilder} 构建的远程 JWKS，或测试用的 ImmutableJWKSet
     * @param algorithms 允许的签名算法
     */
    public JwkSetJwtVerifier(JWKSource<SecurityContext> jwkSource, Collection<JWSAlgorithm> algorithms) {
        Objects.requireNonNull(jwkSource, "jwkSource");
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("algorithms must not be empty");
        }
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(new LinkedHashSet<>(algorithms), jwkSource));
        this.processor = jwtProcessor;
    }

    /**
     * 使用默认算法（RSA、EC 族）。
     */
    public JwkSetJwtVerifier(JWKSource<SecurityContext> jwkSource) {
        this(jwkSource, defaultAlgorithms());
    }

    /**
     * 从远程 JWKS 端点获取公钥，允许 RSA、EC 族签名算法。
     */
    public static JwkSetJwtVerifier withJwkSetUri(String jwkSetUri) {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("jwkSetUri must not be empty");
        }
        try {
            return new JwkSetJwtVerifier(JWKSourceBuilder.create(new URL(jwkSetUri.trim())).build());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid jwkSetUri: " + jwkSetUri, e);
        }
    }

    public static Set<JWSAlgorithm> defaultAlgorithms() {
        Set<JWSAlgorithm> algorithms = new LinkedHashSet<>(JWSAlgorithm.Family.RSA);
        algorithms.addAll(JWSAlgorithm.Family.EC);
        return algorithms;
    }

    /**
     * 校验 token 并返回 claims。
     *
     * @throws JwtVerificationException token 格式错误、签名无效、已过期、算法不允许或无法获取公钥
     */
    public JWTClaimsSet verifyClaimsSet(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtVerificationException("token must not be empty");
        }
        try {
            return processor.process(token, null);
        } catch (ParseException | BadJOSEException | JOSEException ex) {
            throw new JwtVerificationException("Failed to verify JWT: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> verify(String token) {
        return verifyClaimsSet(token).getClaims();
    }
}
