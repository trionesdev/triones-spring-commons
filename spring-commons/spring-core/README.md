# Triones Spring Core Commons

[中文文档](README_zh.md)

A core utility library for Spring Boot applications, providing essential helpers for context management, operation auditing, event handling, and permission control.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.trionesdev.commons</groupId>
    <artifactId>spring-core</artifactId>
    <version>${triones-spring-commons.version}</version>
</dependency>
```

## Features

### 1. Spring Context Helper

Easily access Spring Beans from non-managed classes (static context).

**Usage:**

```java
// Get a bean by type
MyService myService = SpringContextHolder.getBean(MyService.class);

// Get a bean by name
Object myBean = SpringContextHolder.getBean("myBean");
```

### 2. Operation Audit

Annotation-based operation auditing to record user actions, changes, and business events.

**Usage:**

Annotate your service methods with `@OperationAudit`.

```java
@OperationAudit(
    type = "ORDER",
    action = "CREATE",
    subjectId = "#request.orderId",
    description = "Create a new order"
)
public void createOrder(OrderRequest request) {
    // ... business logic
}
```

You can customize how audit logs are handled by implementing `OperationAuditHandler`.

### 3. Act Events

Execute SpEL (Spring Expression Language) logic at different stages of method execution (Before, After, Around, etc.). This is useful for lightweight side effects without creating full Aspects.

**Annotations:**
- `@ActEventBefore`: Run SpEL before method execution.
- `@ActEventAfter`: Run SpEL after method execution (finally).
- `@ActEventAfterReturning`: Run SpEL after successful execution.
- `@ActEventAfterThrowing`: Run SpEL when an exception occurs.
- `@ActEventAround`: Custom around advice.

**Example:**

```java
@ActEventAfter(value = "@notificationService.send('Order created: ' + #orderId)")
public void createOrder(String orderId) {
    // ...
}
```

### 4. Act Permission

Simple, annotation-based permission checking using SpEL expressions.

**Usage:**

```java
@ActPermission(value = "#user.role == 'ADMIN'")
public void adminOnlyOperation(User user) {
    // ...
}
```

If the expression evaluates to `false`, a permission denied exception will be thrown.
