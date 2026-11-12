package com.trionesdev.spring.security.auth.client.jwks;

/**
 * JWT 校验失败：格式错误、签名无效、已过期、算法不允许或无法获取 JWKS。
 */
public class JwtVerificationException extends RuntimeException {
    public JwtVerificationException(String message) {
        super(message);
    }

    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
