package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jwt.JWTClaimsSet;

import java.util.Set;

/**
 * 签发前调整 access token 的 claims，例如加入租户、角色等，供资源服务器的 JwtActorResolver 解析。
 * 在默认 claims（iss、sub、client_id、scope、ACTOR_ID、iat、exp、jti）写入之后调用，可覆盖。
 */
@FunctionalInterface
public interface TokenClaimsCustomizer {
    void customize(JWTClaimsSet.Builder claims, RegisteredClient client, Set<String> authorizedScopes);
}
