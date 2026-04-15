---
description: "Compare Go errx package with Java errx-java implementation. Lists all Go public functions/types, finds their Java equivalents, and reports parity status: ported, partial, or missing."
user_invocable: true
---

# /check-go-parity

You are comparing the Go `errx` package with its Java port `errx-java` to find gaps.

## Input

The user will provide one of:
- A Go file path or directory (e.g., `~/GoProjects/errx/`)
- A specific Go file (e.g., `tools.go`)
- No input = compare entire package

## Step 1: Read Go Source

Read all `.go` files in the Go errx package (excluding `_test.go` files). For each file, extract:
- Exported functions (capitalized names)
- Exported types (interfaces, structs, type aliases)
- Exported methods on types
- Exported constants

Key Go source files:
- `error_x.go` — ErrorX interface + errorX struct + New/Wrap/clone
- `options.go` — WithCode, WithType, WithDetails, WithFields, WithTracePrefix
- `tools.go` — AsErrorX, GetCode, GetType, IsCodeIn, WrapWithTypeOnCodes
- `trace.go` — addTrace with runtime.Caller
- `conv.go` — gRPC conversion (ToGRPCError / FromGRPCError)
- `types.go` — Type enum + M/D type aliases

## Step 2: Read Java Source

Read all Java source files in `errx-java`:
- `errx-core/src/main/java/io/github/stackflowdev/errx/*.java`
- `errx-spring/src/main/java/io/github/stackflowdev/errx/spring/*.java`

For each class/enum, extract:
- Public methods (static and instance)
- Public constructors / factory methods
- Public fields / constants
- Inner classes (e.g., Builder)

## Step 3: Build Mapping Table

Create a comparison table mapping each Go export to its Java equivalent:

```markdown
| Go Source | Go Function/Type | Java Equivalent | Status | Notes |
|-----------|-----------------|-----------------|--------|-------|
| error_x.go | New(msg, opts...) | ErrorX.create(msg) | ✅ Ported | Builder pattern instead of opts |
| error_x.go | Wrap(err, opts...) | ErrorX.wrap(cause) | ✅ Ported | Returns null for null input |
| tools.go | WrapWithTypeOnCodes() | — | ❌ Missing | Not yet implemented |
| conv.go | ToGRPCError() | — | ❌ Missing | Needs errx-grpc module |
```

### Status Definitions
- **✅ Ported** — Fully implemented with matching behavior
- **⚠️ Partial** — Implemented but missing some behavior or edge cases
- **❌ Missing** — Not yet implemented in Java
- **🔄 Different** — Intentionally different from Go (explain why)

## Step 4: Check Behavioral Parity

For each "✅ Ported" item, verify:
1. Same null/nil handling behavior
2. Same default values
3. Same merge/concatenation logic (details pipe separator)
4. Same trace format
5. Same immutability guarantees

Flag any behavioral differences as **⚠️ Partial** with explanation.

## Step 5: Check Test Coverage

For each ported feature, check if tests exist:
- Are there corresponding test methods in the Java test files?
- Do tests cover the same scenarios as Go tests?

## Output

Provide a clear report with:

1. **Summary** — X of Y features ported, Z missing
2. **Full mapping table** (as shown above)
3. **Behavioral differences** (if any) — explain what's different and whether it's intentional
4. **Missing features** — prioritized list of what still needs to be ported
5. **Test gaps** — features that are ported but lack tests
