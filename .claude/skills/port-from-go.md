---
description: "Port a Go source file or feature from the errx Go package to idiomatic Java. Reads Go code, applies Go→Java mapping rules from CLAUDE.md, checks what's already implemented, and generates Java code + tests."
user_invocable: true
---

# /port-from-go

You are porting Go code from the `errx` package to Java for the `errx-java` project.

## Input

The user will provide one of:
- A Go file path (e.g., `~/GoProjects/errx/conv.go`)
- A feature name (e.g., "WrapWithTypeOnCodes", "gRPC conversion")
- A Go code snippet

## Step 1: Read and Understand

1. Read the Go source file or snippet provided
2. Read `.claude/CLAUDE.md` for the full set of mapping rules and design decisions
3. Read the existing Java code to understand what's already implemented:
   - `errx-core/src/main/java/io/github/code19m/errx/ErrorX.java`
   - `errx-core/src/main/java/io/github/code19m/errx/ErrorType.java`
   - Any other relevant files in the target module

## Step 2: Plan the Mapping

Apply these Go → Java mappings:

| Go Pattern | Java Pattern |
|-----------|-------------|
| `error` interface | `RuntimeException` (unchecked) |
| Functional Options (`WithCode`, `WithType`) | Builder pattern (`.code()`, `.type()`) |
| `runtime.Caller()` | `StackWalker.getInstance()` |
| Package-level functions (`errx.GetCode`) | Static methods on `ErrorX` |
| `type M = map[string]string` | `Map<String, String>` |
| `type D = map[string]any` | `Map<String, Object>` |
| Nil check + return nil | Null check + return null |
| Go `errors.As()` | Java `instanceof` pattern matching |

### Code Style Rules (from CLAUDE.md)
- **No Lombok** — use records and manual builders
- **Java 17** — use `instanceof` pattern matching, sealed classes if needed
- **Immutability** — `Collections.unmodifiableMap()` for returned maps
- **Comments** explain reasoning, not syntax
- **Package** — `io.github.code19m.errx` (core) or `io.github.code19m.errx.spring` (spring)

## Step 3: Check for Existing Implementation

Before writing new code:
- Search the Java codebase for any existing implementation of the feature
- If partially implemented, extend rather than rewrite
- If fully implemented, inform the user — do not duplicate

## Step 4: Write Java Code

- Place in the correct module and package
- Follow the existing code patterns (see `ErrorX.java` for reference):
  - Section separators: `// ── Section Name ──────────`
  - Javadoc with `@code` references to Go equivalents
  - `@param` / `@return` / `@throws` only when non-obvious
- Use `HashMap` internally, `Collections.unmodifiableMap()` for exposed maps
- Preserve Go's nil-safe / null-safe behavior

## Step 5: Write Tests

Generate JUnit 5 tests following the project's existing pattern:

```java
// Structure
class NewFeatureTest {

    @Nested
    class FeatureGroupTests {

        @Test
        void methodName_scenario() {
            // Arrange
            // Act
            // Assert with AssertJ
            assertThat(result).isEqualTo(expected);
        }
    }
}
```

### Test Requirements
- Use `@Nested` classes grouped by feature
- Use AssertJ assertions (`assertThat`, `assertThatThrownBy`)
- Cover: happy path, edge cases, null handling, immutability
- Test file goes in `src/test/java/` mirroring the main package structure

## Step 6: Verify

Run the tests:
```bash
./gradlew :errx-core:test    # for core module changes
./gradlew :errx-spring:test  # for spring module changes
./gradlew build              # for full build
```

Fix any compilation or test failures before finishing.

## Output

Provide:
1. The new/modified Java source file(s)
2. The test file(s)
3. A brief summary of what was ported and any behavioral decisions made
