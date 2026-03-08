# Triones Spring Core Commons

Spring Boot 应用的核心工具库，提供了上下文管理、操作审计、事件处理和权限控制等基础功能。

## 安装

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-core</artifactId>
    <version>${triones-spring-commons.version}</version>
</dependency>
```

## 功能特性

### 1. Spring 上下文助手 (Spring Context Helper)

在非 Spring 管理的类中（静态上下文）轻松获取 Spring Bean。

**用法：**

```java
// 根据类型获取 Bean
MyService myService = SpringContextHolder.getBean(MyService.class);

// 根据名称获取 Bean
Object myBean = SpringContextHolder.getBean("myBean");
```

### 2. 操作审计 (Operation Audit)

基于注解的操作审计功能，用于记录用户行为、数据变更和业务事件。

**用法：**

在服务方法上添加 `@OperationAudit` 注解。

```java
@OperationAudit(
    type = "ORDER",
    action = "CREATE",
    subjectId = "#request.orderId",
    description = "创建新订单"
)
public void createOrder(OrderRequest request) {
    // ... 业务逻辑
}
```

可以通过实现 `OperationAuditHandler` 接口来自定义审计日志的处理逻辑。

### 3. 行为事件 (Act Events)

在方法执行的不同阶段（前置、后置、环绕等）执行 SpEL (Spring Expression Language) 逻辑。这对于无需创建完整切片即可实现轻量级副作用非常有用。

**注解：**
- `@ActEventBefore`: 在方法执行前运行 SpEL。
- `@ActEventAfter`: 在方法执行后运行 SpEL (finally)。
- `@ActEventAfterReturning`: 在方法成功执行后运行 SpEL。
- `@ActEventAfterThrowing`: 在发生异常时运行 SpEL。
- `@ActEventAround`: 自定义环绕通知。

**示例：**

```java
@ActEventAfter(value = "@notificationService.send('订单已创建: ' + #orderId)")
public void createOrder(String orderId) {
    // ...
}
```

### 4. 行为权限 (Act Permission)

基于 SpEL 表达式的简单注解式权限检查。

**用法：**

```java
@ActPermission(value = "#user.role == 'ADMIN'")
public void adminOnlyOperation(User user) {
    // ...
}
```

如果表达式计算结果为 `false`，将抛出权限拒绝异常。
