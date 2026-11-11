# Triones:SpringCommons:Security

基于 Spring Security 6 的通用安全组件，提供资源服务端 JWT 认证能力：

| 能力 | 包 | 说明 |
| --- | --- | --- |
| **资源服务端 JWT 认证** | `com.trionesdev.spring.security.jwt` | 解析请求中的 JWT，构建 `Actor` 上下文与 Spring Security `Authentication` |

本模块只提供组件，不包含 Spring Boot 自动配置，Bean 的装配由使用方（或 starter）完成。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、Spring Security 6.x、fastjson2、nimbus-jose-jwt，以及 `spring-auth-client`（远程校验、用户 token 透传）。

---

## 一、JWT 认证（资源服务端）

### 工作流程

`JwtAuthenticationFilter`（`OncePerRequestFilter`）对每个请求：

1. 读取请求头 `X-Trace-Id`，没有则生成 UUID，并回写到响应头；
2. 从 `Authorization: Bearer <token>` 中解析 token；WebSocket 握手请求（`Upgrade: websocket`）在没有 Authorization 头时，从查询参数 `token` 读取；
3. 校验并解析 claims：
   - `ResourceServerConfig.enabled = true`：**远程校验**，由 `spring-auth-client` 的 `JwkSetJwtVerifier` 从 `jwt.jwkSetUri` 获取 JWKS（自动缓存，kid 未知时重新拉取），支持 RSA / EC 签名算法；
   - 否则：**本地校验**，使用 `JwtFacade` 解析；
4. 通过 `JwtActorResolver` 把 claims 转成 `Actor`，写入 `ActorContext`；
5. 通过 `ActorAuthoritiesResolver` 计算权限，构造 `JwtAuthenticationToken`（principal 为 `actorId`，credentials 为 `JwtUserDetails`）放入 `SecurityContextHolder`；
6. 请求结束后清理 `ActorContext`。

> token 校验失败时**不会直接返回 401**，而是清空认证信息、记录 warn 日志后以匿名身份继续，是否放行由 Security 的授权规则决定。未认证访问受保护资源时，可配合 `JwtAuthenticationEntryPoint` 返回 JSON 格式的 401：
> ```json
> {"code":"UNAUTHORIZED","message":"未认证"}
> ```

### 配置类 `ResourceServerConfig`

| 属性 | 说明 |
| --- | --- |
| `enabled` | 是否使用远程 JWKS 校验，默认 `false`（本地校验） |
| `jwt.issuerUri` | 授权服务器 issuer |
| `jwt.tokenUri` | 授权服务器 token 端点 |
| `jwt.jwkSetUri` | JWKS 地址，`enabled = true` 时必填 |

### 使用示例

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConfigurationProperties(prefix = "triones.security.resource-server")
    public ResourceServerConfig resourceServerConfig() {
        return new ResourceServerConfig();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(ResourceServerConfig resourceServerConfig,
                                                           JwtFacade jwtFacade,
                                                           ActorContext actorContext) {
        // 后两个参数传 null 时使用默认实现
        return new JwtAuthenticationFilter(resourceServerConfig, jwtFacade, actorContext,
                new DefaultJwtActorResolver(), new DefaultActorAuthoritiesResolver());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApplicationContext context,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/public/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
            // 把 JwtAuthenticationFilter 加在 UsernamePasswordAuthenticationFilter 之后
            .with(new JwtServerConfigurer<>(context, jwtAuthenticationFilter), c -> { });
        return http.build();
    }
}
```

```yaml
triones:
  security:
    resource-server:
      enabled: true
      jwt:
        jwk-set-uri: https://auth.example.com/oauth2/jwks
```

### 扩展点

**`JwtActorResolver`**：claims → `Actor`。默认实现 `DefaultJwtActorResolver` 读取 `ActorConstants` 中定义的 claim（actorId、userId、role、tenantId、memberId、tenantMemberId、attributes）。claim 结构不同时自定义：

```java
JwtActorResolver resolver = claims -> Actor.builder()
        .actorId((String) claims.get("sub"))
        .tenantId((String) claims.get("tenant"))
        .build();
```

**`ActorAuthoritiesResolver`**：`Actor` → 权限列表。默认实现返回空列表，按需实现：

```java
ActorAuthoritiesResolver authoritiesResolver = actor ->
        actor.getRole() == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole()));
```

### 在业务代码中获取当前用户

```java
Actor actor = actorContext.getActor();

JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
JwtUserDetails details = (JwtUserDetails) authentication.getCredentials();
String token = details.getToken();   // 原始 token，可用于向下游透传用户身份
```

---

> - 服务间调用 token 管理（client_credentials）与获取 JWKS 校验 token：`spring-auth-client`（包 `com.trionesdev.spring.security.auth.client`）。
> - 对外暴露 JWKS 端点、签发 token（token 端点）：`spring-authorization-server`（包 `com.trionesdev.spring.security.authorization.server`）。
