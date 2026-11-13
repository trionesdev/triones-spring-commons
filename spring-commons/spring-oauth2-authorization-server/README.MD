# Triones:SpringCommons:AuthorizationServer

基于 [Spring Authorization Server](https://spring.io/projects/spring-authorization-server) 的授权服务器扩展：让**平台自己的登录**（账号密码、短信验证码、切换租户等）也由授权服务器签发令牌，并在令牌中写入平台 `ACTOR_*` 声明。包 `com.trionesdev.spring.security.oauth2.server.authorization`。

| 能力 | 入口 | 说明 |
| --- | --- | --- |
| 平台登录签发令牌 | `EmbeddedOauth2TokenIssuer` | 实现 `TokenIssuer`：业务校验身份后在进程内签发 access_token + refresh_token，并支持刷新 |
| 令牌声明定制 | `ActorTokenCustomizer` | `OAuth2TokenCustomizer<JwtEncodingContext>`：写入 `client_id` 与平台 `ACTOR_*` 声明 |
| 加载签名密钥 | `RASLoader` | 从 keystore 加载 RSA 密钥；未配置时生成临时密钥 |

授权端点（`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/oauth2/introspect`、`/oauth2/revoke`、OIDC 等）由 Spring Authorization Server 提供。资源服务器侧把令牌转成 `Actor` 见 `spring-security-oauth2`。

本模块只提供组件，不包含自动配置。Spring Boot 应用推荐使用 `oauth2-authorization-server-spring-boot-starter`（`com.trionesdev.springboot`），见下文「使用 starter」。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-oauth2-authorization-server</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、`spring-security-oauth2-authorization-server`、`spring-security`（`TokenIssuer`、`KeyStoreConfig`）、`spring-security-oauth2`（`Oauth2Constants`）、`spring-webmvc`。

---

## 平台登录签发令牌 `EmbeddedOauth2TokenIssuer`

### 用法

```java
// 登录：业务侧完成身份校验后签发
AuthToken token = tokenIssuer.issue(user.getId(), JwtClaims.builder()
        .userId(user.getId()).role("USER").tenantId(tenantId).memberId(memberId).build());

// 刷新
AuthToken refreshed = tokenIssuer.refresh(token.getRefreshToken());
```

`subject` 写入令牌的 `sub`；`JwtClaims` 转为 `ACTOR_*` 声明，其中 `ACTOR_ID` 与 `ACTOR_USER_ID` 都取 `userId`。`AuthToken` 包含 `accessToken`、`refreshToken`、`expiresAt`、`refreshExpiresAt`。

### 工作方式

- 令牌归属一个**平台客户端**（构造参数 `clientId`），授权类型为自定义的 `PLATFORM_GRANT_TYPE`（`urn:triones:params:oauth:grant-type:platform-sign-in`）。该类型没有在 `/oauth2/token` 注册，外部无法用它申请令牌，只能由本类在进程内签发；
- 由授权服务器的 `OAuth2TokenGenerator` 生成令牌，与其他令牌一样：用授权服务器的 RSA 密钥签名（`/oauth2/jwks` 可验签），写入授权记录（`OAuth2AuthorizationService`），可通过 `/oauth2/introspect` 内省、`/oauth2/revoke` 撤销；
- `JwtClaims` 转换出的 `ACTOR_*` 声明存放在授权记录的属性 `Oauth2Constants.ACTOR_CLAIMS_KEY` 中，由 `ActorTokenCustomizer` 写入 access_token。**刷新时沿用登录时的声明**，角色、租户等变化后需重新登录才能体现在令牌中；
- `refresh` 只接受平台客户端以 `PLATFORM_GRANT_TYPE` 签发的 refresh_token，第三方客户端走 `/oauth2/token`；
- refresh_token 默认轮换：每次刷新返回新的 refresh_token，旧的立即失效；平台客户端设置 `reuseRefreshTokens=true` 时沿用旧的；
- refresh_token 为空、不存在、已过期、已撤销或不属于平台登录时，抛出 `BusinessException(INVALID_REFRESH_TOKEN)`（「登录已失效，请重新登录」）。

### 要求

- 平台客户端已登记，且授权类型包含 `refresh_token`（否则无法生成 refresh_token，抛出 `IllegalStateException`）；
- access_token 格式为 JWT（`TokenSettings` 的 `accessTokenFormat` 为 `SELF_CONTAINED`，默认值）；
- `OAuth2TokenGenerator` 中的 `JwtGenerator` 设置了 `ActorTokenCustomizer`，否则令牌中没有 `ACTOR_*` 声明；
- issuer：在授权服务器端点之外签发时，优先取 `AuthorizationServerSettings#getIssuer()`；未配置时取**当前 HTTP 请求**的地址。在没有请求上下文的线程（MQ 消费、定时任务等）中签发时，必须配置 issuer。

### 手动接入（不使用 starter）

```java
@Bean
OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
    var jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
    jwtGenerator.setJwtCustomizer(new ActorTokenCustomizer());
    return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(),
            new OAuth2RefreshTokenGenerator());
}

@Bean
TokenIssuer tokenIssuer(AuthorizationServerSettings settings, RegisteredClientRepository clients,
                        OAuth2AuthorizationService authorizationService,
                        OAuth2TokenGenerator<OAuth2Token> tokenGenerator) {
    return new EmbeddedOauth2TokenIssuer("platform", settings, clients, authorizationService, tokenGenerator);
}
```

同一个 `tokenGenerator` 也要交给授权服务器端点（`OAuth2AuthorizationServerConfigurer#tokenGenerator`），这样 `/oauth2/token` 签发的令牌同样带有 `client_id`。

---

## 令牌声明定制 `ActorTokenCustomizer`

只处理 access_token：

| claim | 来源 |
| --- | --- |
| `client_id` | 所有 access_token：签发令牌的客户端 ID |
| `ACTOR_ID`、`ACTOR_USER_ID`、`ACTOR_ROLE`、`ACTOR_TENANT_ID`、`ACTOR_MEMBER_ID` 等 | 授权记录属性 `Oauth2Constants.ACTOR_CLAIMS_KEY`，即 `EmbeddedOauth2TokenIssuer` 签发的平台令牌；值为 `null` 的声明不写入 |

声明与 JWT 模式下 `JwtFacade` 签发的令牌一致，资源服务器的 `DefaultJwtActorResolver` 无需改动。client_credentials 和授权码模式签发的令牌没有 `ACTOR_*`，资源服务器以 `sub` 作为 `actorId`。

---

## 加载签名密钥 `RASLoader`

```java
RSAKey rsaKey = RASLoader.loadRsaKey(keyStoreConfig);
JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
```

- `KeyStoreConfig.location` 为空时**每次启动随机生成** 2048 位 RSA 密钥并打印警告：重启后已签发的令牌全部失效，多实例之间也互不认可，只能用于开发环境；
- 否则按 `type`（默认 `PKCS12`）、`password`、`alias`、`keyPassword`（为空时使用 keystore 密码）加载，找不到时抛出 `IllegalStateException`；
- 只支持 RSA 密钥。

生成 keystore：

```bash
keytool -genkeypair -alias oauth2 -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -keystore oauth2-jwk.p12 -storepass changeit -dname "CN=oauth2"
```

---

## 使用 starter

`oauth2-authorization-server-spring-boot-starter` 在 `triones.security.authorization.mode=oauth2` 时：

- 用 JDBC 存储客户端、授权记录、授权同意（Spring Authorization Server 的三张表 `oauth2_registered_client`、`oauth2_authorization`、`oauth2_authorization_consent`，需自行建表）；
- 启动时把 `default-client` 写入客户端表（已存在则不覆盖），并作为 `EmbeddedOauth2TokenIssuer` 的平台客户端；
- 开启授权服务器端点（含 OIDC）与表单登录页 `/login`；
- 注册 `TokenIssuer`、`JwtDecoder`（资源服务器链复用）、`OAuth2TokenGenerator`（带 `ActorTokenCustomizer`）；
- 注册 `OAuth2AuthorizationServerCustomizer` Bean 可进一步定制 `OAuth2AuthorizationServerConfigurer`。

```yaml
triones:
  security:
    authorization:
      mode: oauth2
    oauth2:
      authorization-server:
        jwk:
          key-store:
            location: file:/etc/app/oauth2-jwk.p12
            password: ${OAUTH2_KEYSTORE_PASSWORD}
            alias: oauth2
        default-client:
          client-id: platform
          client-secret: ${OAUTH2_PLATFORM_SECRET}
          authorization-grant-types: [client_credentials, refresh_token]   # 默认值
          access-token-ttl: 2h
          refresh-token-ttl: 30d
          reuse-refresh-tokens: false
```
