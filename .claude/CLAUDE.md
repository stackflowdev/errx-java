# errx-java

Java port of [github.com/stackflowdev/errx](https://github.com/stackflowdev/errx) — structured error handling package for Go.

## What This Project Is

A Gradle multi-module Java library that provides structured, extensible error handling with error codes, types, traces, validation fields, and debugging details. The Go version is the **source of truth** for behavior — Java implementation must match its semantics while using idiomatic Java patterns.

## Project Structure

```
errx-java/
├── errx-core/          # Zero-dependency core library (JDK 17 only)
│   └── io.github.stackflowdev.errx
│       ├── ErrorType   # Enum: INTERNAL, VALIDATION, NOT_FOUND, CONFLICT, AUTH, FORBIDDEN, THROTTLING
│       └── ErrorX      # Main class: RuntimeException + code/type/fields/details/trace + Builder + static utils
├── errx-spring/        # Spring Boot integration (optional module)
│   └── io.github.stackflowdev.errx.spring
│       ├── ErrorResponse          # JSON response record
│       ├── ErrorXHandler          # @RestControllerAdvice global exception handler
│       └── ErrorXAutoConfiguration # Spring Boot auto-configuration (@AutoConfiguration)
```

## Key Design Decisions

### Go → Java Pattern Mapping
- Go `error` interface → Java `RuntimeException` (unchecked)
- Go Functional Options (`WithCode`, `WithType`) → Java Builder pattern (`ErrorX.create("msg").code("X").build()`)
- Go `runtime.Caller()` → Java `StackWalker` (Java 9+, efficient single-frame capture)
- Go package-level functions (`errx.GetCode`, `errx.AsErrorX`) → Java static methods on `ErrorX` class

### HTTP Status Mapping (CRITICAL)
- `NOT_FOUND` → **400** (NOT 404). 404 is reserved for routing-level "no such endpoint" only.
- `VALIDATION` → 400
- `INTERNAL` → 500
- `CONFLICT` → 409
- `AUTH` → 401
- `FORBIDDEN` → 403
- `THROTTLING` → 429

### Error Metadata
- `code` (String) — machine-readable error code, default: "UNSPECIFIED"
- `type` (ErrorType) — error category enum, default: INTERNAL
- `fields` (Map<String, String>) — validation field errors (e.g. {"email": "invalid format"})
- `details` (Map<String, Object>) — debugging metadata, never exposed to client
- `trace` (String) — custom propagation trace: `[File.java:42] Class.method → [Other.java:18] Other.call`

### Details Merge Logic
When wrapping an ErrorX and adding details with a duplicate key:
- If both values are strings → concatenate: `"new_value | old_value"`
- Otherwise → new value replaces old value

### Trace Behavior
- Every `ErrorX.create().build()` captures caller frame via StackWalker
- Every `ErrorX.wrap(ex).build()` prepends new caller frame to existing trace
- `tracePrefix("service-name")` adds `>>> service-name >>>` prefix for cross-service propagation
- Trace format: `[FileName.java:line] SimpleClassName.methodName`

### Immutability
- `fields()` and `details()` return unmodifiable maps
- `ErrorX` instances are effectively immutable after construction
- `wrap()` clones metadata from original, never mutates it

## Build & Test

```bash
./gradlew build          # compile + test all modules
./gradlew :errx-core:test   # test core only
./gradlew :errx-spring:test # test spring module only
```

## Tech Stack
- Java 17+
- Gradle (Kotlin DSL)
- JUnit 5 + AssertJ for testing
- Spring Boot 3.4.x (errx-spring module only, compileOnly dependency)

## Code Style
- Comments explain reasoning, not syntax
- English for all code, comments, and variable names
- No Lombok — use records and manual builders
- Prefer `instanceof` pattern matching (Java 17)
- Tests use nested `@Nested` classes grouped by feature

## Claude Code Workflow

### Plans
- ALWAYS save plans inside the project: `.claude/plans/` directory (NEVER use global `~/.claude/plans/`)
- Use descriptive file names (e.g., `spring-validation.md`, `auto-config.md`)
- Each plan must have a `## Status` section (TODO / IN PROGRESS / COMPLETED)
- At the start of a new conversation, check `.claude/plans/` for existing plans first

### Skills
This project has 3 custom skills (`.claude/skills/`):
- `/port-from-go` — Read Go source file and port to idiomatic Java + write tests
- `/test-feature` — Generate JUnit 5 tests for a Java class (@Nested, AssertJ)
- `/check-go-parity` — Compare Go and Java implementations, report parity status

## Go Reference Behavior

When in doubt about how something should behave, check the Go source:
- `error_x.go` — ErrorX interface + errorX struct + New/Wrap/clone
- `options.go` — WithCode, WithType, WithDetails, WithFields, WithTracePrefix
- `tools.go` — AsErrorX, GetCode, GetType, IsCodeIn, WrapWithTypeOnCodes
- `trace.go` — addTrace with runtime.Caller
- `types.go` — Type enum + M/D type aliases

## Status
All planned features are implemented.
