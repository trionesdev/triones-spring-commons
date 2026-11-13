# Triones:SpringCommons:Security

基于 Spring Security 6 的通用安全组件，**JWT 模式**下的资源服务端认证，以及 JWT / OAuth2 两种模式共用的扩展点与模型。

| 能力 | 入口 | 说明 |
| --- | --- | --- |
| JWT 认证 | `jwt.JwtAuthenticationFilter`、`jwt.JwtServerConfigurer` | 用 `JwtFacade` 本地校验请求中的 JWT，构建 `Actor` 与 Spring Security `Authentication` |
| 未认证响应 | `jwt.JwtAuthenticationEntryPoint` | 返回 JSON 格式的 401 |
| claims → Actor | `jwt.JwtActorResolver` / `DefaultJwtActorResolver` | JWT 与 OAuth2 模式共用 |
| Actor → 权限 | `ActorAuthoritiesResolver` / `DefaultActorAuthoritiesResolver` | JWT 与 OAuth2 模式共用 |
| 请求信息 | `ActorRequestSupport` | 生成 / 回写 traceId，填充客户端 IP、User-Agent；两种模式共用 |
| 令牌签发接口 | `TokenIssuer` | 登录后签发 / 刷新令牌，JWT 模式与授权服务器各有实现 |
| 配置模型 | `SecurityHttpAuthorizeRule`、`KeyStoreConfig` | 供 starter 绑定配置 |

OAuth2 资源服务器模式见 `spring-security-oauth2`，OAuth2 授权服务器见 `spring-oauth2-authorization-server`。

本模块只提供组件，不包含自动配置。Spring Boot 应用推荐使用 `security-spring-boot-starter`（`com.trionesdev.springboot`）：`triones.security.authorization.mode=jwt`（默认）时自动装配下文的过滤器链。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、Spring Security 6.x、`commons-core`（`JwtFacade`）、`commons-context`（`ActorContext`）、`commons-exception`、fastjson2。

---

## JWT 认证

### 工作流程

`JwtAuthenticationFilter`（`OncePerRequestFilter`）对每个请求：

1. 读取请求头 `X-Request-TraceId`，没有则生成 UUID，并回写到响应头；
2. 写入 `ActorContext` 一个只有 traceId、时间、客户端 IP、User-Agent 的 `Actor`（未登录请求也有这些信息）；
3. 读取 token：
   - `Authorization: Bearer <token>`；
   - 不带 `Bearer ` 前缀的 `Authorization` 头按原始 token 处理；
   - WebSocket 握手请求（`Upgrade: websocket`）没有 `Authorization` 头时，读取查询参数 `token`；
4. 用 `JwtFacade` 校验签名与有效期并解析 claims；
5. `JwtActorResolver` 把 claims 转成 `Actor`，补上第 2 步的请求信息后写入 `ActorContext`；
6. `ActorAuthoritiesResolver` 计算权限，构造 `JwtAuthenticationToken` 放入 `SecurityContextHolder`：
   - principal：`actorId`；
   - credentials：`JwtUserDetails`（原始 token、actorId、userId、role、tenantId、memberId、attributes）；
7. 请求结束后清理 `ActorContext`。

> token 校验失败时**不会直接返回 401**：清空认证信息、记录 warn 日志后以匿名身份继续，是否放行由授权规则决定。未认证访问受保护资源时，配合 `JwtAuthenticationEntryPoint` 返回：
> ```json
> {"code":"UNAUTHORIZED","message":"未认证"}
> ```

客户端 IP 依次取 `X-Forwarded-For` 的第一个地址、`X-Real-IP`、`request.getRemoteAddr()`。这些请求头可被客户端伪造，只在可信代理之后使用，或由网关覆盖。

### 手动接入（不使用 starter）

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApplicationContext context,
                                                   JwtFacade jwtFacade, ActorContext actorContext) throws Exception {
        // 后两个参数传 null 时使用默认实现
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtFacade, actorContext,
                new DefaultJwtActorResolver(), new DefaultActorAuthoritiesResolver());
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/public/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
            // 把 JwtAuthenticationFilter 加在 UsernamePasswordAuthenticationFilter 之后
            .with(new JwtServerConfigurer<>(context, jwtFilter), Customizer.withDefaults());
        return http.build();
    }
}
```

`JwtFacade`（`commons-core`）决定签名算法与密钥；使用 starter 时由 `triones.security.jwt.*` 配置。

### 在业务代码中获取当前用户

```java
Actor actor = actorContext.getActor();   // 推荐：两种模式下用法一致

// 需要原始 token 时（JWT 模式）
var authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
String token = ((JwtUserDetails) authentication.getCredentials()).getToken();
```

---

## 扩展点

**`JwtActorResolver`**：claims → `Actor`。默认实现 `DefaultJwtActorResolver` 读取 `ClaimsKeyConstant`（`commons-core`）中的 claim：

| claim | Actor 字段 |
| --- | --- |
| `ACTOR_ID` | `actorId` |
| `ACTOR_USER_ID` | `userId` |
| `ACTOR_ROLE` | `role` |
| `ACTOR_TENANT_ID` | `tenantId` |
| `ACTOR_MEMBER_ID` | `memberId` |
| `ACTOR_ATTRIBUTES` | `attributes`（Map） |

claim 结构不同时自定义：

```java
@Bean
JwtActorResolver jwtActorResolver() {
    return claims -> Actor.builder()
            .actorId((String) claims.get("sub"))
            .tenantId((String) claims.get("tenant"))
            .build();
}
```

**`ActorAuthoritiesResolver`**：`Actor` → 权限列表。默认实现返回空列表：

```java
@Bean
ActorAuthoritiesResolver actorAuthoritiesResolver() {
    return actor -> actor.getRole() == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole()));
}
```

使用 starter 时，容器中的这两个 Bean 会被 JWT 与 OAuth2 资源服务器自动使用。

---

## 其他类型

**`TokenIssuer`**：业务侧完成身份校验（账号密码、短信验证码等）后签发令牌，以及用 refresh_token 换新令牌：

```java
AuthToken token = tokenIssuer.issue(userId, jwtClaims);   // accessToken、refreshToken、expiresAt、refreshExpiresAt
AuthToken refreshed = tokenIssuer.refresh(token.getRefreshToken());
```

实现由 starter 按模式提供：JWT 模式为 `security-spring-boot-starter` 的 `JwtTokenIssuer`，OAuth2 模式为 `spring-oauth2-authorization-server` 的 `EmbeddedOauth2TokenIssuer`。

**`SecurityHttpAuthorizeRule`**：接口授权规则，starter 把 `triones.security.rules` 依次应用到 `authorizeHttpRequests`：

| 字段 | 说明 |
| --- | --- |
| `matchers` | 路径匹配模式 |
| `method` | HTTP 方法，可选 |
| `action` | `PERMIT_ALL`、`DENY_ALL`、`AUTHENTICATED`（默认）、`ANONYMOUS`、`HAS_ROLE`、`HAS_ANY_ROLE`、`HAS_AUTHORITY`、`HAS_ANY_AUTHORITY` |
| `roles` / `authorities` | `HAS_*` 规则使用；`HAS_ROLE`、`HAS_AUTHORITY` 只允许一个值 |

```yaml
triones:
  security:
    rules:
      - matchers: [/api/auth/**]
        action: permit_all
      - matchers: [/api/admin/**]
        action: has_role
        roles: [ADMIN]
```

**`KeyStoreConfig`**：keystore 配置（`location`、`type` 默认 `PKCS12`、`password`、`alias`、`keyPassword` 为空时使用 keystore 密码），授权服务器用它加载签名密钥。
