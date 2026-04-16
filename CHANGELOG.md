# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.2.0] — Java-native redesign

This release rewrites the library around Java conventions. It is a
**breaking change** relative to `0.1.0`. If you are upgrading, see the
migration guide below.

### Changed

- **Renamed `ErrorX` to `ErrxException`** throughout the codebase.
  - `ErrorXHandler` → `ErrxExceptionHandler`
  - `ErrorXAutoConfiguration` → `ErrxExceptionAutoConfiguration`
  - Static factories now start with `ErrxException.create()`, `ErrxException.wrap(...)`,
    `ErrxException.asErrxException(...)`, `ErrxException.wrapWithTypeOnCodes(...)`
- **Response body no longer includes `type`.** The HTTP status code conveys
  the category; clients should rely on `code` + HTTP status.
- **`code` is now an i18n message key.** `ErrxExceptionHandler` resolves the
  exception's `code` through Spring's `MessageSource` using the locale from
  `Accept-Language`. Explicit messages (`.message(...)`) still bypass lookup.
- `create()` no longer requires a message parameter. Use `.message(String)` on the
  builder when you want to provide a literal (non-translated) message.
- Default code changed from `"UNSPECIFIED"` to `"unspecified"` to fit the
  lower-case-dotted key convention (`user.not_found`, `order.invalid`).

### Added

- `ErrxException.args(Object...)` — positional arguments for `MessageFormat`
  placeholders in resource bundles (`{0}`, `{1}`, …).
- `ErrxException.hasExplicitMessage()` — signals whether the caller set a
  literal message; used by the handler to decide whether to consult
  `MessageSource`.
- `.message(String)` builder method for setting a literal message.
- Spring auto-configuration now injects any available `MessageSource` into
  `ErrxExceptionHandler` (optional; handler falls back to the raw `code` if
  absent).

### Removed

- **Custom trace string (`trace`, `tracePrefix`).** Java captures full stack
  traces automatically and exposes the cause chain via `getCause()`. Use
  `log.error("...", ex)` to log both.
- `StackWalker`-based trace capture logic.
- `createf(...)` — use `ErrxException.create().message(String.format(...))`
  or let the i18n bundle format through `args(...)` instead.
- `type` field from `ErrorResponse`. It remains on the exception and drives
  HTTP status / log level, but is no longer sent to clients.

### Migration guide (0.1.0 → 0.2.0)

Replace class references:

```java
// Before
import io.github.stackflowdev.errx.ErrorX;

throw ErrorX.create("User not found")
        .code("USER_NOT_FOUND")
        .type(ErrorType.NOT_FOUND)
        .build();

// After
import io.github.stackflowdev.errx.ErrxException;

throw ErrxException.create()
        .code("user.not_found")
        .type(ErrorType.NOT_FOUND)
        .args(userId)
        .build();
```

Define translations in `messages_*.properties`:

```properties
user.not_found=User not found (ID: {0})
```

If you relied on the `type` field in API responses, switch to the HTTP status
code or keep a copy of your old handler that adds `type` back.

Remove any code that read `ex.trace()` — use Java stack traces and
`getCause()` instead.

## [0.1.0]

Initial release.
