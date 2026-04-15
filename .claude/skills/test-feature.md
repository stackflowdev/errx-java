---
description: "Generate JUnit 5 tests for a Java class in errx-java. Reads the target class, analyzes public API, and creates comprehensive tests following the project's nested @Nested class pattern with AssertJ assertions."
user_invocable: true
---

# /test-feature

You are writing tests for the `errx-java` project.

## Input

The user will provide one of:
- A class name (e.g., "ErrorXHandler", "ErrorResponse")
- A file path (e.g., `errx-spring/src/main/java/.../ErrorXHandler.java`)
- A specific method or feature to test

## Step 1: Read the Target

1. Read the target class source code
2. Read existing tests (if any) to avoid duplication
3. Read `ErrorXTest.java` as the reference for test style and patterns

## Step 2: Analyze the Public API

List all testable public methods/constructors. For each, identify:
- Input parameters and their types
- Return type and possible values
- Side effects
- Edge cases (null, empty, boundary values)
- Exception scenarios

## Step 3: Design Test Structure

Organize tests using `@Nested` classes grouped by feature:

```java
package io.github.code19m.errx.spring; // match the target package

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TargetClassTest {

    // ── Feature Group 1 ─────────────────────────────────────────────

    @Nested
    class FeatureGroupTests {

        @Test
        void methodName_happyPath() {
            // ...
        }

        @Test
        void methodName_edgeCase() {
            // ...
        }
    }

    // ── Feature Group 2 ─────────────────────────────────────────────

    @Nested
    class AnotherFeatureTests {
        // ...
    }
}
```

## Step 4: Write Tests

### Naming Convention
- `methodName_scenario` (e.g., `wrap_nullReturnsNull`, `getCode_fromPlainException`)
- Use descriptive names — the test name should explain what's being verified

### Assertion Style (AssertJ only)
```java
// Value assertions
assertThat(result).isEqualTo(expected);
assertThat(result).isNull();
assertThat(result).isNotBlank();
assertThat(result).contains("substring");
assertThat(result).startsWith("prefix");

// Collection assertions
assertThat(map).containsEntry("key", "value");
assertThat(map).isEmpty();
assertThat(list).hasSize(3);

// Exception assertions
assertThatThrownBy(() -> dangerousCall())
        .isInstanceOf(UnsupportedOperationException.class);

// Identity assertions
assertThat(result).isSameAs(original);
```

### Required Coverage
1. **Happy path** — normal usage with valid inputs
2. **Default values** — verify defaults when optional params are omitted
3. **Null handling** — null inputs should behave like Go's nil handling
4. **Immutability** — returned collections must be unmodifiable
5. **Edge cases** — empty strings, empty maps, boundary values
6. **Error conditions** — expected exceptions with correct types/messages

### Things to Avoid
- No mocking unless absolutely necessary (prefer real objects)
- No test utilities or helpers for one-time use
- No `@BeforeEach` setup unless 3+ tests share the same setup
- No Lombok
- No comments explaining obvious assertions

## Step 5: Place and Run

- Test file location: mirror the `src/main/java` package in `src/test/java`
- Run tests:
```bash
./gradlew :errx-core:test    # core module
./gradlew :errx-spring:test  # spring module
```

Fix any failures before finishing.

## Output

Provide:
1. The complete test file
2. Number of test methods and what they cover
3. Any gaps or edge cases that couldn't be tested (explain why)
