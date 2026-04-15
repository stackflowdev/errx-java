# errx-java

Structured error handling for Java. Create rich, typed errors with codes, traces, validation fields, and debugging details.

## Features

- Error codes and types for machine-readable error handling
- Automatic caller trace capture via `StackWalker`
- Validation field errors (`Map<String, String>`)
- Debugging details (never exposed to clients)
- Spring Boot integration with auto-configuration
- Bean Validation support (`@Valid`, `@Validated`)

## Modules

| Module | Description | Dependencies |
|--------|-------------|-------------|
| `errx-core` | Core library | JDK 17 only (zero dependencies) |
| `errx-spring` | Spring Boot integration | Spring Boot 3.x |

## Quick Start

### Gradle

```kotlin
// Core only
implementation("io.github.stackflowdev:errx-core:0.1.0")

// With Spring Boot integration (includes core)
implementation("io.github.stackflowdev:errx-spring:0.1.0")
```

### Maven

```xml
<dependency>
    <groupId>io.github.stackflowdev</groupId>
    <artifactId>errx-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

### Create an error

```java
throw ErrorX.create("User not found")
        .code("USER_NOT_FOUND")
        .type(ErrorType.NOT_FOUND)
        .build();
```

### Create with validation fields

```java
throw ErrorX.create("Validation failed")
        .code("INVALID_INPUT")
        .type(ErrorType.VALIDATION)
        .fields(Map.of(
            "email", "invalid format",
            "age", "must be at least 18"
        ))
        .build();
```

### Wrap an existing exception

```java
try {
    database.query(sql);
} catch (SQLException e) {
    throw ErrorX.wrap(e)
            .code("DB_QUERY_FAILED")
            .details(Map.of("query", sql))
            .build();
}
```

### Formatted messages

```java
throw ErrorX.createf("User %s not found in %s", userId, tableName)
        .code("USER_NOT_FOUND")
        .type(ErrorType.NOT_FOUND)
        .build();
```

### Extract error info from any throwable

```java
String code = ErrorX.getCode(exception);       // "USER_NOT_FOUND" or "UNSPECIFIED"
ErrorType type = ErrorX.getType(exception);     // NOT_FOUND or INTERNAL
boolean match = ErrorX.isCodeIn(exception, "USER_NOT_FOUND", "ORDER_NOT_FOUND");
ErrorX errorX = ErrorX.asErrorX(exception);     // Convert any exception to ErrorX
```

### Conditionally change error type

```java
// Change type to VALIDATION if error code matches
ErrorX result = ErrorX.wrapWithTypeOnCodes(exception, ErrorType.VALIDATION,
        "INVALID_EMAIL", "INVALID_PHONE");
```

### Cross-service trace propagation

```java
throw ErrorX.wrap(remoteException)
        .tracePrefix("order-service")
        .build();
// Trace: >>> order-service >>> [OrderService.java:42] OrderService.process
```

## Error Types and HTTP Status Codes

| ErrorType | HTTP Status | Description |
|-----------|------------|-------------|
| `INTERNAL` | 500 | Unexpected server error |
| `VALIDATION` | 400 | Invalid user input |
| `NOT_FOUND` | 400 | Resource not found (not 404 — see note below) |
| `CONFLICT` | 409 | Resource already exists |
| `AUTH` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `THROTTLING` | 429 | Rate limit exceeded |

> **Note:** `NOT_FOUND` maps to HTTP 400, not 404. HTTP 404 is reserved for routing-level "no such endpoint" responses. A missing business resource (e.g., user not found) is a client input error.

## Spring Boot Integration

Add `errx-spring` to your classpath — it auto-configures everything:

```kotlin
implementation("io.github.stackflowdev:errx-spring:0.1.0")
```

That's it. No `@Import`, no `@ComponentScan`, no configuration needed.

### What you get automatically

- **ErrorX exception handler** — converts `ErrorX` into structured JSON responses
- **Bean Validation** — `MethodArgumentNotValidException` and `ConstraintViolationException` are handled
- **Logging** — server errors (5xx) logged at ERROR, client errors (4xx) at WARN
- **Security** — `details` and `trace` are never exposed to clients

### JSON response format

```json
{
  "code": "USER_NOT_FOUND",
  "type": "NOT_FOUND",
  "message": "User not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

With validation errors:

```json
{
  "code": "VALIDATION",
  "type": "VALIDATION",
  "message": "Validation failed",
  "fields": {
    "email": "must be a valid email",
    "name": "must not be blank"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Custom handler

To customize, define your own `ErrorXHandler` bean — auto-configuration will back off:

```java
@Bean
public ErrorXHandler errorXHandler() {
    return new MyCustomErrorXHandler();
}
```

## Error Trace

Every `ErrorX` automatically captures where it was created or wrapped:

```
[UserService.java:42] UserService.findById
```

When wrapped multiple times, traces chain:

```
[UserController.java:28] UserController.getUser → [UserService.java:42] UserService.findById
```

Cross-service propagation:

```
>>> api-gateway >>> [Gateway.java:15] Gateway.forward → [UserService.java:42] UserService.findById
```

## Build

```bash
./gradlew build              # compile + test all modules
./gradlew :errx-core:test    # test core only
./gradlew :errx-spring:test  # test spring module only
```

## Requirements

- Java 17+
- Spring Boot 3.x (for `errx-spring` module only)

## License

MIT
