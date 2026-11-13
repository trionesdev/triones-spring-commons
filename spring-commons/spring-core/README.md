# Triones:SpringCommons:Core

基于 Spring 的通用业务组件：注解驱动的操作审计、方法事件、权限校验、分布式锁，以及缓存门面、接口版本路由和静态获取 `ApplicationContext`。

| 能力 | 入口 | 包 |
| --- | --- | --- |
| 操作审计 | `@OperationAudit` + `OperationAuditHandler` | `com.trionesdev.spring.core.audit` |
| 方法事件（SpEL 钩子） | `@ActEventBefore` / `@ActEventAfter` / `@ActEventAfterReturning` / `@ActEventAfterThrowing` / `@ActEventAround` | `com.trionesdev.spring.core.event.act` |
| 方法权限校验 | `@ActPermission` | `com.trionesdev.spring.core.permission.act` |
| 分布式锁 | `@Lock` | `com.trionesdev.spring.lock` |
| 缓存门面 | `CacheFacade` 及 Redis / Caffeine / Simple / NoOp 实现 | `com.trionesdev.spring.cache` |
| 接口版本 | `@PathApiVersion` / `@HeaderApiVersion` | `com.trionesdev.spring.web.apiversion` |
| 静态获取容器 | `SpringContextHolder` / `SpringContextSetter` | `com.trionesdev.spring.core.context` |

本模块只提供组件，不包含自动配置。Spring Boot 应用引入 `spring-boot-starter`（`com.trionesdev.springboot`）即可自动注册下文的切面、`CacheFacade` 与版本路由；不使用 starter 时按各节说明自行注册 Bean。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-core</artifactId>
</dependency>
```

版本由 `spring-commons-dependencies` 统一管理。依赖 Java 17、AspectJ、`commons-core`、`commons-exception`、`commons-lock`。以下依赖为 optional，用到对应功能时由使用方引入：`spring-webmvc`（接口版本）、`caffeine`（`CaffeineCacheFacade`）、`spring-data-redis`（`RedisCacheFacade`）。

所有注解只对 Spring 代理的 Bean 生效：同一个类内部的方法互相调用不会经过切面。SpEL 表达式中可用 `#参数名` 引用方法参数（需编译时保留参数名，Spring Boot 默认开启 `-parameters`），用 `@beanName` 引用容器中的 Bean。

---

## 操作审计 `@OperationAudit`

在方法上声明审计元数据，由 `OperationAuditAspect` 在方法执行前后采集请求、结果、耗时与成败，交给 `OperationAuditHandler` 处理（写库、发消息等）。

```java
@OperationAudit(domain = "order", action = "UPDATE", subject = "订单",
        subjectId = "#cmd.orderId", description = "修改订单")
public void updateOrder(UpdateOrderCmd cmd) { ... }
```

| 属性 | 说明 |
| --- | --- |
| `type` / `category` / `domain` / `action` / `subject` | 审计分类信息，原样写入上下文 |
| `subjectId` | 操作对象 ID，SpEL 表达式 |
| `description` / `descriptionResourceKey` | 描述及其国际化 key |
| `batch` | 是否批量操作 |
| `handler` | 指定处理器类；不指定时使用 `isDefault()` 返回 `true` 的处理器 |

实现处理器并注册为 Bean：

```java
@Component
public class DbOperationAuditHandler extends OperationAuditHandler {
    @Override
    public Boolean isDefault() {
        return true;                       // 默认处理器，最多只能有一个
    }

    @Override
    public String beforeContent(OperationAuditContext context, Map<String, Object> args) {
        return toJson(orderRepository.findById(context.getSubjectId()));   // 执行前快照（可选）
    }

    @Override
    public String afterContent(OperationAuditContext context, Map<String, Object> args) {
        return toJson(orderRepository.findById(context.getSubjectId()));   // 执行后快照（可选，仅成功时调用）
    }

    @Override
    public void handle(OperationAuditContext context) {
        // context：request（参数名 → 参数值）、response、before/afterContent、startAt、endAt、success、errorMsg ...
        auditLogRepository.save(toAuditLog(context));
    }
}
```

- 没有可用的处理器时（未注册任何处理器，或未指定 `handler` 且没有默认处理器），方法照常执行，不记录审计；
- 存在多个默认处理器，或 `handler` 指定的类不是已注册的 Bean 时，抛出异常；
- 方法抛出异常时 `success=false`、`errorMsg` 为异常信息，异常照常抛出，`handle` 仍会被调用。

### 对比变更内容

`OperationAuditUtils.changeContent` 按指定属性比较执行前后的 JSON 快照，得到新增 / 删除 / 修改项：

```java
OperationChangeContent change = OperationAuditUtils.changeContent(
        context.getBeforeContent(), context.getAfterContent(),
        List.of(CompareProperty.builder().property("status").name("状态").build(),
                CompareProperty.builder().property("address.city").name("城市").build()));   // 支持 a.b 嵌套属性
if (change.isNotEmpty()) { ... }
```

前后都为空或相等的属性忽略；之前为空记为 `add`，之后为空记为 `remove`，否则记为 `update`。JSON 解析使用 `JacksonUtils`。

---

## 方法事件 `@ActEvent*`

在方法执行的不同时机执行一段 SpEL，通常用来调用某个 Bean 发布领域事件、记录日志：

| 注解 | 执行时机 | 额外变量 |
| --- | --- | --- |
| `@ActEventBefore("...")` | 方法执行前 | — |
| `@ActEventAfter("...")` | 方法结束后（无论成败） | — |
| `@ActEventAfterReturning("...")` | 方法正常返回后 | `#returnValue` |
| `@ActEventAfterThrowing("...")` | 方法抛出异常后 | `#ex` |
| `@ActEventAround(before = "...", after = "...")` | `before` 在执行前求值，`after` 在正常返回后求值 | `after` 中可用 `#beforeData`（`before` 的结果） |

```java
@ActEventAfterReturning("@orderEventPublisher.created(#returnValue)")
public Order createOrder(CreateOrderCmd cmd) { ... }

@ActEventAround(before = "@orderRepository.findById(#id)", after = "@orderEventPublisher.changed(#beforeData, #id)")
public void updateOrder(String id, UpdateOrderCmd cmd) { ... }
```

不使用 starter 时注册对应的切面 Bean：`ActEventBeforeAspect`、`ActEventAfterAspect`、`ActEventAfterReturningAspect`、`ActEventAfterThrowingAspect`、`ActEventAroundAspect`。

---

## 方法权限校验 `@ActPermission`

方法执行前对 SpEL 求值，结果不为 `true` 时抛出 `PermissionDeniedException`：

```java
@ActPermission(value = "@permissionChecker.canEdit(#orderId)", description = "编辑订单")
public void updateOrder(String orderId, UpdateOrderCmd cmd) { ... }
```

不使用 starter 时注册 `ActPermissionAspect`。

---

## 分布式锁 `@Lock`

```java
@Lock(key = "'order:pay:' + #orderId", waitTime = 3, leaseTime = 30, unit = TimeUnit.SECONDS)
public void pay(String orderId) { ... }
```

| 属性 | 说明 |
| --- | --- |
| `key` | 锁名，SpEL 表达式 |
| `waitTime` | 获取锁的最长等待时间，默认 0（不等待） |
| `leaseTime` | 持有锁的时长，默认 0，具体语义由 `commons-lock` 的锁实现决定 |
| `unit` | 时间单位，默认毫秒 |

获取锁失败时抛出 `RuntimeException("lock failed")`。锁由 `commons-lock` 的 `TrionesLockTemplate` 提供；不使用 starter 时注册 `new LockAspect(lockTemplate)`。

---

## 缓存门面 `CacheFacade<K, V>`

统一的键值缓存接口，同时可操作 Spring `CacheManager` 中的具名缓存：

```java
cacheFacade.set("captcha:" + phone, code, Duration.ofMinutes(5));
String code = cacheFacade.get("captcha:" + phone);
cacheFacade.evict("captcha:" + phone);

// 具名缓存（委托给 CacheManager）
cacheFacade.set("users", userId, user);
User user = cacheFacade.get("users", userId, User.class);
cacheFacade.evict("users", userId);
cacheFacade.clean("users");
```

| 实现 | 键值存储 | 过期时间 |
| --- | --- | --- |
| `RedisCacheFacade` | `RedisTemplate#opsForValue` | 支持 |
| `CaffeineCacheFacade` | 进程内 Caffeine，可用 `setCacheSpecification` / `setCaffeineSpec` / `setCaffeine` 配置 | 仅当 Caffeine 配置了可变过期（`expireAfter(Expiry)`）时按条目生效，否则忽略 |
| `SimpleCacheFacade` | 进程内 `ConcurrentHashMap` | 忽略 |
| `NoOpCacheFacade` | 不存储，`get` 恒为 `null` | — |

使用 starter 时根据 `spring.cache.type`（未设置时按 Spring Boot 的自动检测）注册对应实现。

---

## 接口版本 `@PathApiVersion` / `@HeaderApiVersion`

同一路径按版本路由到不同的处理方法，可标在类或方法上：

```java
// 路径版本：/api/v1/orders 与 /api/v2/orders 由不同方法处理
@GetMapping("/api/{version}/orders")
@PathApiVersion("v1")
public List<OrderV1> listV1() { ... }

@GetMapping("/api/{version}/orders")
@PathApiVersion("v2")
public List<OrderV2> listV2() { ... }

// 请求头版本：按请求头的值匹配
@GetMapping("/api/orders")
@HeaderApiVersion("2")
public List<OrderV2> listByHeader() { ... }
```

两种方式二选一，通过替换 Spring MVC 的 `RequestMappingHandlerMapping` 生效：

- `PathApiVersionRequestMappingHandlerMapping(pathVersionName)`：路径变量名，默认 `version`；
- `HeaderApiVersionRequestMappingHandlerMapping(headerVersionName)`：请求头名，无参构造默认 `X-Api-Version`。

使用 starter 时配置：

```yaml
triones:
  web:
    api-version:
      type: path            # path 或 header，不配置则不启用
      path:
        version-name: version
      header:
        version-name: X-Version
```

不使用 starter 时通过 `WebMvcRegistrations` 返回上述 HandlerMapping。

---

## 静态获取容器 `SpringContextHolder`

在无法注入的场景（静态方法、非 Spring 管理的对象）中获取 `ApplicationContext`：

```java
OrderService orderService = SpringContextHolder.getApplicationContext().getBean(OrderService.class);
```

`SpringContextSetter` 是一个 `ApplicationContextInitializer`，在容器刷新前写入 `SpringContextHolder`。需要注册后才生效，例如在 `META-INF/spring.factories` 中：

```properties
org.springframework.context.ApplicationContextInitializer=com.trionesdev.spring.core.context.SpringContextSetter
```

或 `new SpringApplication(App.class).addInitializers(new SpringContextSetter())`。
