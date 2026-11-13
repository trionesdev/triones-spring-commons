# TrionesDev Spring 基础公共包

基于 Spring Framework / Spring Security 的通用组件库：操作审计、方法事件、分布式锁、缓存门面、接口版本，以及 JWT / OAuth2 两种模式下的认证与令牌签发。

本仓库只提供组件，不包含 Spring Boot 自动配置。Spring Boot 应用推荐使用配套的 [triones-spring-boot](https://github.com/trionesdev/triones-spring-boot)，通过配置即可启用。

## 模块

| 模块 | artifactId | 说明 |
| --- | --- | --- |
| [Core](spring-commons/spring-core/README.md) | `spring-core` | 操作审计 `@OperationAudit`、方法事件 `@ActEvent*`、权限校验 `@ActPermission`、分布式锁 `@Lock`、缓存门面 `CacheFacade`、接口版本 `@PathApiVersion` / `@HeaderApiVersion`、`SpringContextHolder` |
| [Security](spring-commons/spring-security/README.MD) | `spring-security` | JWT 模式的资源服务端认证（`JwtAuthenticationFilter`），以及两种模式共用的 `JwtActorResolver`、`ActorAuthoritiesResolver`、`TokenIssuer` |
| [SecurityOauth2](spring-commons/spring-security-oauth2/README.MD) | `spring-security-oauth2` | OAuth2 模式的资源服务端：把 access_token 转成平台 `Actor` |
| [AuthorizationServer](spring-commons/spring-oauth2-authorization-server/README.MD) | `spring-oauth2-authorization-server` | 基于 Spring Authorization Server：平台登录签发令牌（`EmbeddedOauth2TokenIssuer`）、令牌写入 `ACTOR_*` 声明 |
| Dependencies | `spring-commons-dependencies` | BOM，统一管理本仓库模块及 Spring、Spring Security 等第三方依赖的版本 |

### 认证模式

| | JWT 模式 | OAuth2 模式 |
| --- | --- | --- |
| 签发令牌 | `JwtFacade`（`commons-core`） | 授权服务器（`spring-oauth2-authorization-server`） |
| 校验令牌 | `spring-security`：`JwtFacade` 本地校验 | `spring-security-oauth2`：Spring Security 资源服务器，按 JWKS 校验 |
| 适用场景 | 单体或共享密钥的服务 | 需要标准 OAuth2 端点、第三方客户端、令牌撤销 / 内省 |

两种模式下业务代码都通过 `ActorContext#getActor()` 获取当前用户，切换模式无需修改业务代码。

## 环境要求

- Java 17+
- Spring Framework 6.2、Spring Security 6.5（具体版本见 `spring-commons-dependencies`）

## 快速开始

在项目中导入 BOM，再按需引入模块（无需写版本号）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.trionesdev.commons</groupId>
            <artifactId>spring-commons-dependencies</artifactId>
            <version>6.0.1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.trionesdev.commons</groupId>
        <artifactId>spring-core</artifactId>
    </dependency>
</dependencies>
```

各模块的用法见上表中的模块文档。

### 仓库

SNAPSHOT 版本发布在 TrionesDev 的 Maven 仓库：

```xml
<repositories>
    <repository>
        <id>trionesdev</id>
        <url>https://maven.cnb.cool/trionesdev/mvn/-/packages/</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

## 本地构建

```bash
mvn clean install
```

依赖 [triones-commons](https://github.com/trionesdev/triones-commons)（`commons-core`、`commons-context` 等），其版本由 `spring-commons-dependencies` 导入的 `commons-dependencies` 管理。

---

### 关注我们，一起交流

> 留言回复不及时，可以通过关注公众号联系我们

<div style="text-align: center">
<img src="images/shuque_wx.jpg" width="200px" alt="TrionesDev 公众号二维码">
</div>
