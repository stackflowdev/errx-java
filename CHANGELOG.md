# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.0] — Initial release

First public release of `errx-java`, a Java-native structured error handling
library with optional Spring Boot integration.

### Core (`errx-core`, zero-dependency, JDK 17+)

- `ErrxException` — single `RuntimeException` carrying a machine-readable
  `code`, a typed `ErrorType`, positional `args`, validation `fields`, and
  debugging `details`.
- `ErrorType` enum — `INTERNAL`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`,
  `AUTH`, `FORBIDDEN`, `THROTTLING`. Each carries its HTTP status
  (`NOT_FOUND` → 400, because 404 is reserved for routing-level misses).
- Builder-based API: `ErrxException.create().code(...).type(...).args(...).build()`.
- `.message(String)` — opt into an explicit (non-translated) message that
  bypasses i18n lookup.
- Static utilities: `getCode`, `getType`, `isCodeIn`, `asErrxException`,
  `wrapWithTypeOnCodes`, `wrap`.
- Native Java cause chain + stack traces — no custom trace string.
- Immutable after construction: `fields()` and `details()` return unmodifiable
  maps; `args()` returns a defensive copy.

### Spring (`errx-spring`, Spring Boot 3.x)

- `ErrxExceptionHandler` (`@RestControllerAdvice`) — converts `ErrxException`
  into a structured `ErrorResponse` and maps `ErrorType` to HTTP status.
- i18n message resolution — the exception's `code` doubles as the
  `MessageSource` key. Locale is read from `LocaleContextHolder` (populated
  from the `Accept-Language` header).
- Bean Validation support — `MethodArgumentNotValidException` and
  `ConstraintViolationException` are mapped to `validation.failed` with
  per-field messages.
- `ErrxExceptionAutoConfiguration` — registers the handler automatically with
  any available `MessageSource`. Backs off if the user defines their own
  `ErrxExceptionHandler` bean.
- Response body: `{ code, message, fields?, timestamp }`. The exception's
  `type` is intentionally not included — the HTTP status conveys the category.
- Logging — 5xx at ERROR with the full cause chain, 4xx at WARN. `details`
  appear in server logs but never in responses.
