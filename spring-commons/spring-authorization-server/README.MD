# Triones:SpringCommons:AuthorizationServer

授权服务器（token 签发方）组件，包 `com.trionesdev.spring.security.authorization.server`。

| 能力 | 入口 | 说明 |
| --- | --- | --- |
| **签发 token** | `token.TokenEndpointConfigurer` / `token.TokenEndpointFilter` | OAuth2 token 端点，`client_credentials` 模式（RFC 6749 §4.4），用 `JwtFacade` 签发 JWT access token |
| **暴露 JWKS 端点** | `jwks.JwksEndpointConfigurer` / `jwks.JwksEndpointFilter` | 把 `JwtFacade` 的签名公钥（含轮换中的验证公钥）以 JWKS（RFC 7517）格式公开，供其他服务远程校验 token |

获取 JWKS 校验 token、以 client_credentials 申请 token 等客户端能力在 `spring-auth-client` 模块。

本模块只提供组件，不包含 Spring Boot 自动配置。Spring Boot 应用推荐使用 `authorization-server-spring-boot-starter`：通过 `triones.security.authorization-server.*` 配置即可开启 token 端点（含客户端登记）与 JWKS 端点，无需手动接入安全链。下文为不使用 starter 时的手动接入方式。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-authorization-server</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、Spring Security 6.x、`commons-core`（`JwtFacade`）。

## 整体流程

```
服务 A（spring-auth-client）            授权服务器（本模块）                 服务 B（spring-security）
AuthorizedClientManager ──POST /auth/token──▶ TokenEndpointFilter
          ◀── {"access_token": JWT, ...} ───  （JwtFacade 签名）
   ── Authorization: Bearer JWT ─────────────────────────────────────────▶ JwtAuthenticationFilter
                                               JwksEndpointFilter ◀── GET /auth/jwks（缓存）── JwkSetJwtVerifier
```

## token 端点（client_credentials）

### 登记客户端

```java
@Bean
RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
    return new InMemoryRegisteredClientRepository(
            RegisteredClient.withClientId("order-service")
                    .clientSecret(passwordEncoder.encode("..."))   // 存编码后的值；默认编码器下也可直接写 {bcrypt}... / {noop}...
                    .scopes("order.read", "order.write")           // 允许申请的 scope
                    .accessTokenTimeToLive(Duration.ofMinutes(30)) // 可选，默认取端点配置（30 分钟）
                    .clientAuthenticationMethods(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) // 可选，默认 basic 与 post 都允许
                    .build());
}
```

客户端存放在数据库等处时，实现 `RegisteredClientRepository#findByClientId` 即可。

### 接入安全链

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .with(new TokenEndpointConfigurer<>(), Customizer.withDefaults())   // POST /auth/token
        .with(new JwksEndpointConfigurer<>(), Customizer.withDefaults());   // GET  /auth/jwks
    return http.build();
}
```

可选配置（链式调用时需显式写出泛型参数）：

```java
new TokenEndpointConfigurer<HttpSecurity>()
        .path("/oauth2/token")                         // 默认 /auth/token
        .issuer("https://auth.example.com")            // 写入 iss
        .accessTokenTimeToLive(Duration.ofMinutes(10)) // 客户端未单独设置时的有效期，默认 30 分钟
        .claimsCustomizer((claims, client, scopes) -> claims.claim(ClaimsKeyConstant.ACTOR_ROLE, "SERVICE"))
        .registeredClientRepository(repository)        // 默认从容器获取
        .passwordEncoder(passwordEncoder)              // 默认取容器中唯一的 PasswordEncoder，否则 DelegatingPasswordEncoder
        .jwtFacade(jwtFacade);                         // 默认从容器获取
```

- 与 JWKS 端点一样位于认证 / 授权过滤器之前，**无需 `permitAll()`**；
- 自动从 CSRF 保护中排除该端点（客户端凭据认证，不依赖 Cookie 会话），其他接口的 CSRF 不受影响。

### 请求与响应

```http
POST /auth/token
Authorization: Basic base64(urlencode(client_id):urlencode(client_secret))
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=order.read
```

```json
{"access_token":"eyJ...","token_type":"Bearer","expires_in":1800,"scope":"order.read"}
```

- 客户端认证：`client_secret_basic`（按 RFC 6749 §2.3.1 先 URL 解码）或 `client_secret_post`（表单参数 `client_id` / `client_secret`），不能同时使用两种；`client_secret` 不能出现在 URL 查询参数中。
- scope：不传时授予客户端登记的全部 scope；传了则必须是登记范围的子集。
- 响应带 `Cache-Control: no-store`、`Pragma: no-cache`；只接受 `POST`。

| 错误 `error` | 状态码 | 场景 |
| --- | --- | --- |
| `invalid_request` | 400 | 缺少 `grant_type`、参数重复、同时使用两种认证方式、secret 在 URL 中、Basic 头格式错误 |
| `invalid_client` | 401 | 未认证、客户端不存在、secret 错误、认证方式不被该客户端允许（不区分具体原因，避免枚举 clientId）；使用 Basic 时带 `WWW-Authenticate: Basic realm="oauth2"` |
| `unsupported_grant_type` | 400 | `grant_type` 不是 `client_credentials` |
| `invalid_scope` | 400 | 申请了未登记的 scope |

### token 内容

| claim | 值 |
| --- | --- |
| `sub`、`client_id` | clientId |
| `ACTOR_ID` | clientId：资源服务器的 `DefaultJwtActorResolver` 据此得到服务身份；没有 `ACTOR_USER_ID`，业务可据此区分服务调用与用户调用 |
| `scope` | 授予的 scope，空格分隔（RFC 9068）；为空时不写 |
| `iss` | 配置了 `issuer` 时写入 |
| `iat`、`exp`、`jti` | 签发时间、过期时间、唯一 ID |

需要租户、角色等更多信息时使用 `claimsCustomizer`，它在默认 claims 写入之后执行，可以覆盖。

> 签名密钥由 `JwtFacade` 决定。多实例部署或资源服务器通过 JWKS 远程校验时，请使用持久化的 RSA / EC 密钥（不要用每次启动随机生成的临时密钥）。

## JWKS 端点

端点作为过滤器挂在 Spring Security 过滤器链中（与 Spring Authorization Server 的 `NimbusJwkSetEndpointFilter` 做法一致）：

- 位于认证 / 授权过滤器之前并直接写出响应，**无需 `permitAll()`**；
- 仍经过链中的 HeaderWriterFilter、CorsFilter 等，安全响应头与 CORS 配置照常生效；
- 只响应 `GET`，返回 `application/json`，`Cache-Control: public, max-age=…`（`cacheMaxAge` 为 0 时返回 `no-store`）；
- 只输出公钥；HMAC（oct）密钥不能公开，`JwtFacade` 使用 HMAC 时不要开启。

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        // 默认路径 /auth/jwks、缓存 5 分钟；JwtFacade 从容器获取
        .with(new JwksEndpointConfigurer<>(), Customizer.withDefaults());
    return http.build();
}
```

自定义路径、缓存时间或指定 `JwtFacade`（链式调用时需显式写出泛型参数，否则无法推断）：

```java
http.with(new JwksEndpointConfigurer<HttpSecurity>()
        .path("/oauth2/jwks")
        .cacheMaxAge(Duration.ofMinutes(10))
        .jwtFacade(jwtFacade), Customizer.withDefaults());

// 或使用构造器
http.with(new JwksEndpointConfigurer<>(jwtFacade, "/oauth2/jwks", Duration.ofMinutes(10)), Customizer.withDefaults());
```

校验方配置 jwk-set-uri 即可（见 `spring-auth-client` 的 `JwkSetJwtVerifier`，或 `spring-security` 的远程校验）：

```yaml
triones:
  security:
    resource-server:
      enabled: true
      jwt:
        jwk-set-uri: http://auth-service/auth/jwks
```
