# Triones:SpringCommons:SecurityOauth2

**OAuth2 模式**下的资源服务端组件：在 Spring Security 标准 OAuth2 资源服务器（`oauth2ResourceServer().jwt()`）之上，把已验签的 access_token 转成平台 `Actor`，写入 `ActorContext`，业务代码与 JWT 模式（`spring-security`）用法一致。

| 能力 | 入口 | 说明 |
| --- | --- | --- |
| Jwt → 认证结果 | `ActorJwtAuthenticationConverter` | 用 `JwtActorResolver` 解析 Actor，合并 Actor 权限与 scope 权限 |
| 认证结果 | `ActorJwtAuthenticationToken` | 继承 Spring 的 `JwtAuthenticationToken`，额外携带 `Actor` |
| 写入 ActorContext | `ActorContextFilter` | 放在 `BearerTokenAuthenticationFilter` 之后 |
| 常量 | `Oauth2Constants` | 与授权服务器共用的属性名 |

令牌的签名校验由 Spring Security 的 `JwtDecoder` 完成（通常指向授权服务器的 JWKS），本模块只负责 Jwt 之后的部分。授权服务器见 `spring-oauth2-authorization-server`。

本模块只提供组件，不包含自动配置。Spring Boot 应用推荐使用 `security-oauth2-spring-boot-starter`（`com.trionesdev.springboot`）：`triones.security.authorization.mode=oauth2` 时自动装配下文的过滤器链，并沿用 `triones.security.*` 的放行路径与授权规则。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security-oauth2</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、`spring-security-oauth2-resource-server`、`spring-security-oauth2-jose` 与 `spring-security`（`JwtActorResolver`、`ActorAuthoritiesResolver`、`ActorRequestSupport`）。

---

## 工作流程

1. `BearerTokenAuthenticationFilter`（Spring Security）读取 `Authorization: Bearer <token>`，用 `JwtDecoder` 验签；
2. `ActorJwtAuthenticationConverter` 把 `Jwt` 转成 `ActorJwtAuthenticationToken`：
   - `JwtActorResolver` 解析 Actor：平台令牌带 `ACTOR_*` 声明，解析结果与 JWT 模式一致；
   - 没有 `ACTOR_ID` 的令牌（client_credentials、第三方客户端）以 `sub` 作为 `actorId`；
   - 令牌中的 `client_id` 放入 `actor.attributes["client_id"]`，业务可据此区分调用方；
   - 权限 = `ActorAuthoritiesResolver` 的结果 + scope 权限（`SCOPE_xxx`）；
   - `getName()` 为 `actorId`；
3. `ActorContextFilter`：
   - 读取或生成 `X-Request-TraceId`，回写到响应头；
   - 已认证时取令牌中的 Actor，未认证（放行接口）时用空 Actor；
   - 补上 traceId、时间、客户端 IP、User-Agent 后写入 `ActorContext`，请求结束后清理。

token 无效或过期时由 Spring Security 返回 401，不会以匿名身份继续（与 JWT 模式不同）。放行路径如果仍要接受带过期令牌的请求（如登录接口），需让 `BearerTokenResolver` 对这些路径返回 `null`，starter 已按 `triones.security.ignored-matchers` 处理。

## 手动接入（不使用 starter）

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                               ActorContext actorContext) throws Exception {
    var converter = new ActorJwtAuthenticationConverter(new DefaultJwtActorResolver(),
            new DefaultActorAuthoritiesResolver());
    var entryPoint = new JwtAuthenticationEntryPoint();   // JSON 格式的 401
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated())
        .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entryPoint))
        .addFilterAfter(new ActorContextFilter(actorContext), BearerTokenAuthenticationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
    return http.build();
}

@Bean
public JwtDecoder jwtDecoder() {
    // 授权服务器独立部署时；内嵌授权服务器时由其提供 JwtDecoder
    return NimbusJwtDecoder.withJwkSetUri("https://auth.example.com/oauth2/jwks").build();
}
```

`JwtActorResolver`、`ActorAuthoritiesResolver` 的自定义方式见 `spring-security` 的「扩展点」；使用 starter 时注册为 Bean 即可，JWT 与 OAuth2 模式共用。

## 在业务代码中获取当前用户

```java
Actor actor = actorContext.getActor();   // 与 JWT 模式一致

// 需要原始令牌或 claims 时
var authentication = (ActorJwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
String token = authentication.getToken().getTokenValue();
String clientId = authentication.getToken().getClaimAsString("client_id");
```

## 常量

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `Oauth2Constants.ACTOR_CLAIMS_KEY` | `triones.actor_claims` | 授权服务器把平台 `ACTOR_*` 声明存在授权记录（`oauth2_authorization`）的这个属性里，签发和刷新令牌时写入 access_token |
| `ActorJwtAuthenticationConverter.ATTRIBUTE_CLIENT_ID` | `client_id` | 令牌中的 claim 名，同时是 `actor.attributes` 中的 key |
