# errx-java

Structured error handling for Java. One exception type, typed categories,
i18n-ready messages, validation fields, and a zero-config Spring Boot
integration.

## Features

- Single `ErrxException` class with a fluent builder
- Machine-readable `code` that doubles as an i18n message key
- Typed error categories mapped to HTTP status codes
- Validation field errors (`Map<String, String>`)
- Private debugging details (never exposed to clients)
- Spring Boot auto-configuration with `MessageSource` / `Accept-Language` support
- Bean Validation support (`@Valid`, `@Validated`)

## Modules

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `errx-core` | Core library | JDK 17 only (zero dependencies) |
| `errx-spring` | Spring Boot integration | Spring Boot 3.x |

## Quick Start

### Gradle (Kotlin DSL)

```kotlin
// Core only
implementation("io.github.stackflowdev:errx-core:0.2.0")

// With Spring Boot integration (includes core)
implementation("io.github.stackflowdev:errx-spring:0.2.0")
```

### Maven

```xml
<dependency>
    <groupId>io.github.stackflowdev</groupId>
    <artifactId>errx-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Usage

### Create an exception

```java
throw ErrxException.create()
        .code("user.not_found")
        .type(ErrorType.NOT_FOUND)
        .args(userId)
        .build();
```

### With an explicit (non-translated) message

```java
throw ErrxException.create()
        .code("db.query_failed")
        .type(ErrorType.INTERNAL)
        .message("Could not reach primary replica")
        .build();
```

### With validation fields

```java
throw ErrxException.create()
        .code("validation.failed")
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
    throw ErrxException.wrap(e)
            .code("db.query_failed")
            .details(Map.of("query", sql))
            .build();
}
```

### Extract metadata from any throwable

```java
String code = ErrxException.getCode(exception);     // "user.not_found" or "unspecified"
ErrorType type = ErrxException.getType(exception);   // NOT_FOUND or INTERNAL
boolean match = ErrxException.isCodeIn(exception, "user.not_found", "order.not_found");
ErrxException ex = ErrxException.asErrxException(exception);
```

### Conditionally change error type

```java
// Change type to VALIDATION if code matches
ErrxException result = ErrxException.wrapWithTypeOnCodes(
        exception, ErrorType.VALIDATION,
        "invalid.email", "invalid.phone");
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

> **Note:** `NOT_FOUND` maps to HTTP 400, not 404. HTTP 404 is reserved for
> routing-level "no such endpoint" responses. A missing business resource
> (e.g., user not found) is a client input error.

## Spring Boot Integration

Add `errx-spring` to your classpath — it auto-configures everything:

```kotlin
implementation("io.github.stackflowdev:errx-spring:0.2.0")
```

That's it. No `@Import`, no `@ComponentScan`, no configuration needed.

### What you get automatically

- **Exception handler** — converts `ErrxException` into structured JSON responses
- **Bean Validation** — `MethodArgumentNotValidException` and `ConstraintViolationException` handled
- **i18n message resolution** — messages translated via `MessageSource` using `Accept-Language`
- **Logging** — server errors (5xx) at ERROR, client errors (4xx) at WARN
- **Security** — `details` and `type` are never exposed to clients

### JSON response format

```json
{
  "code": "user.not_found",
  "message": "User not found (ID: 42)",
  "timestamp": "2026-04-15T10:30:00Z"
}
```

With validation fields:

```json
{
  "code": "validation.failed",
  "message": "Validation failed",
  "fields": {
    "email": "must be a valid email",
    "name": "must not be blank"
  },
  "timestamp": "2026-04-15T10:30:00Z"
}
```

> The exception's `type` field is deliberately NOT included in the response
> body. It maps 1:1 to the HTTP status code, so clients already have that
> information.

### i18n: messages by `code`

The exception's `code` is also the i18n message key. Spring resolves it
through whatever `MessageSource` you have configured, using the locale from
`Accept-Language`.

```properties
# src/main/resources/messages_en.properties
user.not_found=User not found (ID: {0})
validation.failed=Validation failed

# src/main/resources/messages_uz.properties
user.not_found=Foydalanuvchi topilmadi (ID: {0})
validation.failed=Ma''lumotlar tekshiruvidan o''tmadi
```

```java
throw ErrxException.create()
        .code("user.not_found")
        .type(ErrorType.NOT_FOUND)
        .args(userId)       // → {0} in the bundle message
        .build();
```

Request headers drive which bundle Spring picks:

```
Accept-Language: en  →  "User not found (ID: 42)"
Accept-Language: uz  →  "Foydalanuvchi topilmadi (ID: 42)"
```

If no `MessageSource` is configured, or the key is missing from every
bundle, the handler falls back to the raw `code` as the message.

Configure `MessageSource` in the usual Spring way — for example via
`application.yml`:

```yaml
spring:
  messages:
    basename: messages
    encoding: UTF-8
    always-use-message-format: true  # recommended — keeps apostrophe escaping consistent
```

### Custom handler

To customize, define your own `ErrxExceptionHandler` bean — auto-configuration
will back off:

```java
@Bean
public ErrxExceptionHandler errxExceptionHandler(MessageSource messageSource) {
    return new MyCustomErrxExceptionHandler(messageSource);
}
```

## Observing the cause chain

`ErrxException` preserves Java's native cause chain and stack trace. To debug
propagation, log the exception directly — SLF4J prints the full chain:

```java
try {
    process(request);
} catch (ErrxException ex) {
    log.error("request failed", ex);  // logs full stack trace + getCause() chain
}
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
