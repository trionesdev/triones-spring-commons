# Triones Spring Security Commons

[中文文档](readme_zh.md)

A lightweight Spring Security extension library designed for modern RESTful APIs, providing out-of-the-box support for JWT authentication, JSON-based error handling, and a flexible authentication pipeline.

## Features

- **JWT Authentication**: Built-in support for JWT token extraction, validation, and user detail loading.
- **JSON Error Responses**: Pre-configured `AuthenticationEntryPoint` and `AccessDeniedHandler` that return standard JSON error responses instead of HTML pages or redirects.
- **Flexible Architecture**:
    - **Authentication Executor**: Decouples authentication logic from the filter, allowing custom authentication strategies.
    - **Authentication Interceptor**: Hooks for pre/post-authentication logic (e.g., logging, auditing).
- **Simplified Configuration**: `GeneralAuthenticationConfigurer` to easily integrate custom authentication filters into the Spring Security chain.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-security</artifactId>
    <version>${triones-spring-commons.version}</version>
</dependency>
```

## Quick Start

### 1. Configure Spring Security

Create a `SecurityConfiguration` class to set up the security filter chain.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        // 1. Configure Token Config
        SecurityTokenConfig tokenConfig = new SecurityTokenConfig();
        
        // 2. Configure Authentication Executor (e.g. JWT)
        var authExecutor = new JwtAuthenticationExecutor(tokenConfig);

        // 3. Configure the General Configurer
        GeneralAuthenticationConfigurer<HttpSecurity> authConfigurer = new GeneralAuthenticationConfigurer<>(authExecutor);
        // Optional: Set an interceptor for custom logic
        // authConfigurer.setAuthenticationInterceptor(myInterceptor);

        // 4. Configure HttpSecurity
        http
            // Register the JWT Provider
            .authenticationProvider(new JwtAuthenticationProvider(tokenConfig, myAuthorityManager))
            
            // Disable CSRF for stateless APIs
            .csrf(AbstractHttpConfigurer::disable)
            
            // Apply the custom configurer
            .with(authConfigurer, Customizer.withDefaults())
            
            // Configure Exception Handling for JSON responses
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler())
            )
            
            // Configure Authorization
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

### 2. Implement Authority Manager (Optional)

Implement `AuthorityManager` to provide roles and permissions for authenticated users.

```java
@Bean
public AuthorityManager authorityManager() {
    return new AuthorityManager() {
        @Override
        public List<String> getRoles(Authentication authentication) {
            // Return user roles
            return List.of("USER");
        }

        @Override
        public List<String> getPermissions(Authentication authentication) {
            // Return specific permissions
            return List.of("user:read", "user:write");
        }
    };
}
```

### 3. Generate Tokens

Use `TokenManager` to create tokens upon login.

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final TokenManager tokenManager;

    @PostMapping("/login")
    public Token login() {
        // Authenticate user...
        
        // Create Token
        return tokenManager.createToken(TokenDefinition.builder()
                .subject("userId")
                .claims(Map.of("username", "admin"))
                .build());
    }
}
```

## Key Components

### `JwtAuthenticationExecutor`
Extends `AbstractAuthenticationExecutor`. It extracts the Bearer token from the `Authorization` header or a query parameter (for WebSocket), creates a `JwtAuthenticationToken`, and delegates to the `AuthenticationManager`.

### `GeneralAuthenticationConfigurer`
A `SecurityConfigurerAdapter` that simplifies adding the `GeneralAuthenticationFilter` to the security chain. It ensures the filter is properly initialized with the `AuthenticationManager` and `AuthenticationExecutor`.

### `JsonAuthenticationEntryPoint` & `JsonAccessDeniedHandler`
Standard implementations that write a JSON error response (with appropriate HTTP status codes 401 and 403) to the response stream, making it suitable for frontend frameworks and mobile clients.

### `JwtTokenManager`
A utility bean for creating and parsing JWT tokens based on the provided `SecurityTokenConfig`.
