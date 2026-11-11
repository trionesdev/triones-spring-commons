package com.trionesdev.spring.security.authorization.server.token;

/**
 * 客户端在 token 端点的认证方式（RFC 6749 §2.3.1）。
 */
public enum ClientAuthenticationMethod {
    /**
     * {@code Authorization: Basic base64(urlencode(client_id):urlencode(client_secret))}
     */
    CLIENT_SECRET_BASIC,
    /**
     * 表单参数 {@code client_id} / {@code client_secret}
     */
    CLIENT_SECRET_POST
}
