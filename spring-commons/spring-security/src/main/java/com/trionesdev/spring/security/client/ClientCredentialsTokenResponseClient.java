package com.trionesdev.spring.security.client;

/**
 * 向授权服务器 token 端点发起 client_credentials 请求。
 */
public interface ClientCredentialsTokenResponseClient {

    AuthorizedClient getTokenResponse(ClientRegistration registration);
}
