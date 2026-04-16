# errx-java

Structured error handling for Java — exceptions with machine-readable codes,
typed categories, validation fields, i18n message resolution, and debugging details.

## What This Project Is

A Gradle multi-module Java library built around a single custom exception
type (`ErrxException`) plus a Spring Boot integration that translates it into
structured HTTP responses. The library is designed from Java conventions
outward — it does not mirror any other language's error model.

## Project Structure

```
errx-java/
├── errx-core/          # Zero-dependency core library (JDK 17 only)
│   └── io.github.stackflowdev.errx
│       ├── ErrorType        # Enum: INTERNAL, VALIDATION, NOT_FOUND, CONFLICT, AUTH, FORBIDDEN, THROTTLING
│       └── ErrxException    # RuntimeException + code/type/args/fields/details + Builder + static utils
├── errx-spring/        # Spring Boot integration (optional module)
│   └── io.github.stackflowdev.errx.spring
│       ├── ErrorResponse                    # JSON response record (code, message, fields, timestamp)
│       ├── ErrxExceptionHandler             # @RestControllerAdvice + MessageSource i18n resolver
│       └── ErrxExceptionAutoConfiguration   # Spring Boot auto-configuration (@AutoConfiguration)
```

## Key Design Decisions

### Exception model
- `ErrxException extends RuntimeException` (unchecked)
- Builder pattern: `ErrxException.create().code("x").type(...).args(...).build()`
- Native Java constructs do the heavy lifting: stack traces (`Throwable.getStackTrace`)
  and cause chains (`Throwable.getCause`) — the library does not build a custom
  trace string

### HTTP Status Mapping (CRITICAL)
- `NOT_FOUND` → **400** (NOT 404). 404 is reserved for routing-level
  "no such endpoint" only.
- `VALIDATION` → 400
- `INTERNAL` → 500
- `CONFLICT` → 409
- `AUTH` → 401
- `FORBIDDEN` → 403
- `THROTTLING` → 429

### Exception fields
- `code` (String) — machine-readable code AND i18n message key (e.g.
  `"user.not_found"`), default: `"unspecified"`
- `type` (ErrorType) — category enum, default: `INTERNAL`. Used to derive
  HTTP status and log level. NEVER exposed to the client in the response body.
- `args` (Object[]) — positional arguments for `MessageFormat` placeholders
  (`{0}`, `{1}`, …) in resource bundles
- `fields` (Map<String, String>) — per-field validation errors
- `details` (Map<String, Object>) — debugging metadata, NEVER exposed to clients
- `hasExplicitMessage` (boolean) — when true, the handler skips bundle lookup
  and uses the raw message directly

### i18n resolution
The `code` doubles as the `MessageSource` key. `ErrxExceptionHandler` resolves
messages via Spring's `MessageSource`, using the locale from
`LocaleContextHolder` (populated from the `Accept-Language` header).

Resolution order:
1. `hasExplicitMessage()` → use `getMessage()` verbatim
2. `MessageSource` + `code` + `args` → translated message
3. Fall back to `code` as the literal message

If no `MessageSource` bean is present, every `ErrxException` falls straight
to its `code` as the message.

### Details merge logic
When wrapping an `ErrxException` and adding details with a duplicate key:
- If both values are strings → concatenate: `"new_value | old_value"`
- Otherwise → new value replaces old value

### Immutability
- `fields()` and `details()` return unmodifiable maps
- `ErrxException` instances are effectively immutable after construction
- `wrap()` clones metadata from the original, never mutates it

## Build & Test

```bash
./gradlew build              # compile + test all modules
./gradlew :errx-core:test    # test core only
./gradlew :errx-spring:test  # test spring module only
```

## Tech Stack
- Java 17+
- Gradle (Kotlin DSL)
- JUnit 5 + AssertJ for testing
- Spring Boot 3.4.x (`errx-spring` module only, `compileOnly` dependency)

## Code Style
- Comments explain reasoning, not syntax
- English for all code, comments, and variable names
- No Lombok — use records and manual builders
- Prefer `instanceof` pattern matching (Java 17+)
- Tests use nested `@Nested` classes grouped by feature
- Test assertions via AssertJ

## Claude Code Workflow

### Skills
This project has 2 custom skills (`.claude/skills/`):
- `/test-feature` — Generate JUnit 5 tests for a Java class (`@Nested`, AssertJ)
- `/add-error-code` — Add a new error code with `messages_*.properties` entries across locales

## Status
v0.1.0 — initial public release. `ErrxException` + i18n via `MessageSource` +
Spring Boot auto-configuration.
