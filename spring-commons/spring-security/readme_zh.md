# Triones Spring Security Commons

一个专为现代 RESTful API 设计的轻量级 Spring Security 扩展库，提供开箱即用的 JWT 认证、基于 JSON 的错误处理以及灵活的认证管道支持。

## 功能特性

- **JWT 认证**：内置支持 JWT 令牌的提取、验证和用户详情加载。
- **JSON 错误响应**：预配置的 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`，返回标准的 JSON 错误响应，而非 HTML 页面或重定向。
- **灵活的架构**：
    - **认证执行器 (Authentication Executor)**：将认证逻辑与过滤器解耦，允许自定义认证策略。
    - **认证拦截器 (Authentication Interceptor)**：提供认证前/后的逻辑钩子（如日志记录、审计）。
- **简化配置**：提供 `GeneralAuthenticationConfigurer`，可轻松将自定义认证过滤器集成到 Spring Security 链中。

## 安装

在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security</artifactId>
    <version>${triones-spring-commons.version}</version>
</dependency>
```

## 快速开始

### 1. 配置 Spring Security

创建一个 `SecurityConfiguration` 类来设置安全过滤器链。

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        // 1. 配置 Token 配置
        SecurityTokenConfig tokenConfig = new SecurityTokenConfig();
        
        // 2. 配置认证执行器 (例如 JWT)
        var authExecutor = new JwtAuthenticationExecutor(tokenConfig);

        // 3. 配置通用配置器
        GeneralAuthenticationConfigurer<HttpSecurity> authConfigurer = new GeneralAuthenticationConfigurer<>(authExecutor);
        // 可选：设置拦截器以实现自定义逻辑
        // authConfigurer.setAuthenticationInterceptor(myInterceptor);

        // 4. 配置 HttpSecurity
        http
            // 注册 JWT 提供者
            .authenticationProvider(new JwtAuthenticationProvider(tokenConfig, myAuthorityManager))
            
            // 禁用 CSRF（适用于无状态 API）
            .csrf(AbstractHttpConfigurer::disable)
            
            // 应用自定义配置器
            .with(authConfigurer, Customizer.withDefaults())
            
            // 配置异常处理以返回 JSON 响应
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler())
            )
            
            // 配置授权
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
    
    @Bean
    public TokenManager tokenManager() {
        return new JwtTokenManager(new SecurityTokenConfig());
    }
}
```

### 2. 实现权限管理器 (可选)

实现 `AuthorityManager` 为已认证用户提供角色和权限。

```java
@Bean
public AuthorityManager authorityManager() {
    return new AuthorityManager() {
        @Override
        public List<String> getRoles(Authentication authentication) {
            // 返回用户角色
            return List.of("USER");
        }

        @Override
        public List<String> getPermissions(Authentication authentication) {
            // 返回特定权限
            return List.of("user:read", "user:write");
        }
    };
}
```

### 3. 生成令牌

在登录时使用 `TokenManager` 创建令牌。

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final TokenManager tokenManager;

    @PostMapping("/login")
    public Token login() {
        // 认证用户...
        
        // 创建令牌
        return tokenManager.createToken(TokenDefinition.builder()
                .subject("userId")
                .claims(Map.of("username", "admin"))
                .build());
    }
}
```

## 核心组件

### `JwtAuthenticationExecutor`
扩展自 `AbstractAuthenticationExecutor`。它从 `Authorization` 头或查询参数（用于 WebSocket）中提取 Bearer 令牌，创建 `JwtAuthenticationToken`，并委托给 `AuthenticationManager` 处理。

### `GeneralAuthenticationConfigurer`
一个 `SecurityConfigurerAdapter`，用于简化将 `GeneralAuthenticationFilter` 添加到安全链的过程。它确保过滤器使用 `AuthenticationManager` 和 `AuthenticationExecutor` 正确初始化。

### `JsonAuthenticationEntryPoint` & `JsonAccessDeniedHandler`
标准实现，向响应流写入 JSON 错误响应（带有适当的 HTTP 状态码 401 和 403），非常适合前端框架和移动客户端。

### `JwtTokenManager`
一个实用 Bean，用于根据提供的 `SecurityTokenConfig` 创建和解析 JWT 令牌。
