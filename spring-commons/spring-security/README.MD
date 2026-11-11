# Triones:SpringCommons:Security

基于 Spring Security 6 的通用安全组件，提供两部分能力：

| 能力 | 包 | 说明 |
| --- | --- | --- |
| **资源服务端 JWT 认证** | `com.trionesdev.spring.security.jwt` | 解析请求中的 JWT，构建 `Actor` 上下文与 Spring Security `Authentication` |
| **服务间调用 token 管理** | `com.trionesdev.spring.security.client` | 无用户上下文时以 `client_credentials` 模式申请 token，用于服务间调用（类似 `OAuth2AuthorizedClientManager`） |

本模块只提供组件，不包含 Spring Boot 自动配置，Bean 的装配由使用方（或 starter）完成。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、Spring Security 6.x、OkHttp、fastjson2、nimbus-jose-jwt。

---

## 一、JWT 认证（资源服务端）

### 工作流程

`JwtAuthenticationFilter`（`OncePerRequestFilter`）对每个请求：

1. 读取请求头 `X-Trace-Id`，没有则生成 UUID，并回写到响应头；
2. 从 `Authorization: Bearer <token>` 中解析 token；WebSocket 握手请求（`Upgrade: websocket`）在没有 Authorization 头时，从查询参数 `token` 读取；
3. 校验并解析 claims：
   - `ResourceServerConfig.enabled = true`：**远程校验**，从 `jwt.jwkSetUri` 拉取 JWKS（自动缓存），支持 RSA / EC 签名算法；
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

## 二、服务间调用 token（client_credentials）

在定时任务、消息消费、系统内部调用等**没有用户 token** 的场景，服务以自身身份（`client_id` / `client_secret`）向授权服务器申请 access token 再调用下游服务。

### 核心类

| 类 | 说明 |
| --- | --- |
| `ClientRegistration` | 客户端注册信息：`registrationId`、`clientId`、`clientSecret`、`tokenUri`、`scopes`、`clientAuthenticationMethod` |
| `ClientRegistrationRepository` / `InMemoryClientRegistrationRepository` | 按 `registrationId` 查找注册信息 |
| `AuthorizeRequest` | 授权请求，包含 `clientRegistrationId` 与可选的 `principal` |
| `AuthorizedClientManager` | 入口：获取 token、缓存、过期前自动重新申请 |
| `AuthorizedClient` | 授权结果：`AccessToken`、`RefreshToken`、`tokenType`、`issuedAt`、`expiresAt`、`scopes` |
| `ClientCredentialsTokenResponseClient` / `OkHttpClientCredentialsTokenResponseClient` | 调用 token 端点（RFC 6749 §4.4） |
| `AuthorizedClientOkHttpInterceptor` | OkHttp 拦截器，自动附加 token |
| `AuthorizedClientHttpRequestInterceptor` | RestTemplate / RestClient 拦截器，自动附加 token |
| `AuthorizedClientFeignRequestInterceptor` | Feign 拦截器，有用户时透传用户 token，没有用户时使用服务 token |
| `UserTokenResolver` / `SecurityContextUserTokenResolver` | 解析当前线程的用户 token（默认读取 `JwtAuthenticationFilter` 认证结果） |

### 行为说明

- **缓存**：token 按 `clientRegistrationId` 缓存，同一客户端的所有调用方共享；`principal` 仅作标识，不影响缓存。
- **提前刷新**：在 `expiresAt - clockSkew`（默认 60 秒）后重新申请，可通过 `setClockSkew` 调整；授权服务器未返回 `expires_in` 时视为不过期。
- **并发安全**：同一 `registrationId` 并发获取时只会向授权服务器发起一次请求。
- **401 处理**：
  - OkHttp 拦截器收到 401 时，丢弃缓存、重新申请 token 并**重试一次**；
  - RestTemplate 拦截器收到 401 时只清除缓存（请求体可能不可重放，不自动重试），下一次请求使用新 token。
- **不覆盖已有 token**：请求已带 `Authorization` 头（例如透传的用户 token）时，拦截器不做修改。
- **客户端认证方式**：
  - `CLIENT_SECRET_BASIC`（默认）：`Authorization: Basic base64(clientId:clientSecret)`
  - `CLIENT_SECRET_POST`：`client_id` / `client_secret` 作为表单参数
- **refresh token**：响应中若包含 `refresh_token` 会保存在 `AuthorizedClient` 中，但过期后仍走 `client_credentials` 重新申请。
- 获取失败（非 2xx、响应无 `access_token`、非 JSON、未配置 `tokenUri`、registration 不存在）统一抛出 `AuthorizedClientException`。

### 配置示例

```yaml
triones:
  security:
    clients:
      order-service:                 # key 即 registrationId
        client-id: order-service
        client-secret: ${ORDER_CLIENT_SECRET}
        token-uri: https://auth.example.com/oauth2/token
        scopes: [internal]
        client-authentication-method: client_secret_basic
```

```java
@Configuration
public class ClientConfig {

    @Bean
    @ConfigurationProperties(prefix = "triones.security.clients")
    public Map<String, ClientRegistration> clientRegistrations() {
        return new LinkedHashMap<>();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(Map<String, ClientRegistration> clientRegistrations) {
        // 使用 Map 构造时，key 会作为 registrationId
        return new InMemoryClientRegistrationRepository(clientRegistrations);
    }

    @Bean
    public AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository repository) {
        AuthorizedClientManager manager = new AuthorizedClientManager(repository);
        manager.setClockSkew(Duration.ofSeconds(30));
        return manager;
    }
}
```

也可以直接用代码注册：

```java
ClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(List.of(
        ClientRegistration.builder()
                .registrationId("order-service")
                .clientId("order-service")
                .clientSecret("secret")
                .tokenUri("https://auth.example.com/oauth2/token")
                .scopes(Set.of("internal"))
                .build()));
```

### 使用方式

**直接获取 token**

```java
AuthorizedClient client = manager.authorize(AuthorizeRequest.of("order-service"));
request.header("Authorization", client.getAuthorizationHeaderValue());   // Bearer xxx

// 带 principal
manager.authorize(AuthorizeRequest.withClientRegistrationId("order-service").principal("order-sync-job").build());

// 只要 token 字符串
String token = manager.getAccessToken("order-service");

// 下游明确提示 token 失效时，手动清除缓存
manager.remove("order-service");
```

**OkHttp**

```java
OkHttpClient http = new OkHttpClient.Builder()
        .addInterceptor(new AuthorizedClientOkHttpInterceptor(manager, "order-service"))
        .build();
```

**RestTemplate / RestClient**

```java
RestTemplate restTemplate = new RestTemplateBuilder()
        .additionalInterceptors(new AuthorizedClientHttpRequestInterceptor(manager, "order-service"))
        .build();

RestClient restClient = RestClient.builder()
        .requestInterceptor(new AuthorizedClientHttpRequestInterceptor(manager, "order-service"))
        .build();
```

**Feign（区分有用户 / 无用户）**

需要使用方自行引入 Feign（本模块中 `feign-core` 为 optional 依赖）。`AuthorizedClientFeignRequestInterceptor` 按以下顺序决定 `Authorization` 头：

| 场景 | 行为 | 下游看到的身份 |
| --- | --- | --- |
| 调用前已显式设置 `Authorization` | 不修改 | 调用方指定 |
| 当前线程有用户 token（Web 请求线程，经 `JwtAuthenticationFilter` 认证） | 透传 `Bearer <用户token>` | 用户 |
| 没有用户 token（定时任务、MQ 消费、`@Async`、线程池等） | 申请 client_credentials token | 服务 |
| `setRelayUserToken(false)` | 始终使用服务 token | 服务 |

```java
// 全局生效：所有 FeignClient 都会带上 token
@Bean
public RequestInterceptor authorizedClientFeignRequestInterceptor(AuthorizedClientManager manager) {
    return new AuthorizedClientFeignRequestInterceptor(manager, "order-service");
}
```

只对某个 FeignClient 生效时，放到该 client 的独立配置中（该配置类**不要**被组件扫描到，否则会变成全局）：

```java
@FeignClient(name = "inventory", configuration = InventoryFeignConfig.class)
public interface InventoryClient { ... }

public class InventoryFeignConfig {
    @Bean
    public RequestInterceptor inventoryAuthInterceptor(AuthorizedClientManager manager) {
        AuthorizedClientFeignRequestInterceptor interceptor =
                new AuthorizedClientFeignRequestInterceptor(manager, "order-service");
        // 内部管理接口只认服务身份时，关闭用户 token 透传
        interceptor.setRelayUserToken(false);
        return interceptor;
    }
}
```

其他用法：

```java
// 只透传用户 token，没有用户时不加 Authorization 头
new AuthorizedClientFeignRequestInterceptor(null, null, new SecurityContextUserTokenResolver());

// 自定义用户 token 来源（例如从 ActorContext 或自定义线程上下文读取）
new AuthorizedClientFeignRequestInterceptor(manager, "order-service", () -> myContext.currentToken());
```

> 注意事项：
> - `SecurityContextHolder` 默认是线程本地的，切换线程后读不到用户 token，会自动降级为服务 token。如需在异步线程中继续使用用户身份，可使用 `DelegatingSecurityContextExecutor` / `DelegatingSecurityContextRunnable` 传递上下文，或设置 `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL`。
> - 透传的用户 token 过期由下游返回 401，本拦截器不处理；服务 token 由 `AuthorizedClientManager` 提前刷新。若服务 token 被授权服务器提前吊销，可在 Feign `ErrorDecoder` 中遇到 401 时调用 `manager.remove(registrationId)`。

**自定义 token 请求**

token 端点不是标准 OAuth2 格式，或需要自定义超时、代理时：

```java
// 自定义 OkHttpClient
OkHttpClient tokenHttp = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(5)).build();
AuthorizedClientManager manager = new AuthorizedClientManager(repository,
        new OkHttpClientCredentialsTokenResponseClient(tokenHttp));

// 或完全自定义实现
ClientCredentialsTokenResponseClient custom = registration -> AuthorizedClient.builder()
        .clientRegistration(registration)
        .accessToken(AccessToken.of(callMyAuthServer(registration)))
        .expiresAt(Instant.now().plusSeconds(1800))
        .build();
```

> 注意：`clientSecret` 属于敏感信息，请通过环境变量或配置中心注入，不要提交到代码仓库；token 端点必须使用 HTTPS。
