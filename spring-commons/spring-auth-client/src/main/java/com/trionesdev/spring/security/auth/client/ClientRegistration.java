package com.trionesdev.spring.security.auth.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 客户端注册信息（client_credentials 模式），用于服务间调用时向授权服务器申请 token。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClientRegistration {
    /**
     * 注册 ID，作为获取 token 时的 key
     */
    private String registrationId;
    private String clientId;
    private String clientSecret;
    /**
     * 授权服务器 token 端点
     */
    private String tokenUri;
    @Builder.Default
    private Set<String> scopes = new LinkedHashSet<>();
    /**
     * 客户端认证方式，默认 client_secret_basic
     */
    @Builder.Default
    private ClientAuthenticationMethod clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

    public enum ClientAuthenticationMethod {
        /**
         * 通过 Authorization: Basic 头传递 client_id/client_secret
         */
        CLIENT_SECRET_BASIC,
        /**
         * 通过表单参数传递 client_id/client_secret
         */
        CLIENT_SECRET_POST
    }
}
