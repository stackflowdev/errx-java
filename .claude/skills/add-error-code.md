---
description: "Add a new error code to a Spring Boot project that uses errx-java. Creates or updates messages_*.properties bundles across all supported locales with the new key, and shows example usage in a controller plus a test."
user_invocable: true
---

# /add-error-code

Add a new structured error code to a project using `errx-java`.

## Input

The user will provide:
- `code` — the machine-readable identifier AND i18n bundle key (e.g., `order.already_paid`, `user.not_found`)
- `type` — the `ErrorType` enum value (`INTERNAL`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `AUTH`, `FORBIDDEN`, `THROTTLING`)
- Optional: short description and positional argument names (e.g., `{0}=userId`, `{1}=orderId`)

If any are missing, ask the user for them before writing files.

## Step 1: Locate Resource Bundles

Search the project for existing `messages*.properties` files:

```
src/main/resources/messages*.properties
src/test/resources/messages*.properties
```

Identify:
- The default file (`messages.properties`)
- Per-locale files (`messages_en.properties`, `messages_uz.properties`, etc.)

If no bundles exist, create `messages_en.properties` as the default and ask which other locales the project supports.

## Step 2: Add the Key to Each Bundle

For every bundle file, append a line:

```properties
# messages_en.properties
user.not_found=User not found (ID: {0})

# messages_uz.properties
user.not_found=Foydalanuvchi topilmadi (ID: {0})
```

Rules:
- Keep the key identical across bundles
- If you don't know the translation for a locale, leave a TODO stub like `user.not_found=TODO user.not_found (ID: {0})` and flag it in output
- **Escape apostrophes as `''`** whenever the message contains positional args — the bundle will be parsed by `MessageFormat`
- For messages without args, plain `'` is fine (but escaping is still safe)

Preserve file structure: append to the end or group with related keys if an obvious grouping exists (e.g., all `user.*` keys together).

## Step 3: Show Usage Snippets

Print a ready-to-paste controller snippet and a JUnit test snippet. Example for `code="user.not_found"`, `type=NOT_FOUND`, `args=[userId]`:

**Throw:**
```java
throw ErrxException.create()
        .code("user.not_found")
        .type(ErrorType.NOT_FOUND)
        .args(userId)
        .build();
```

**Test:**
```java
@Test
void getUser_missing_throwsErrxException() {
    assertThatThrownBy(() -> service.getUser(999L))
            .isInstanceOf(ErrxException.class)
            .satisfies(ex -> {
                ErrxException ex2 = (ErrxException) ex;
                assertThat(ex2.code()).isEqualTo("user.not_found");
                assertThat(ex2.type()).isEqualTo(ErrorType.NOT_FOUND);
                assertThat(ex2.args()).containsExactly(999L);
            });
}
```

## Step 4: Verify

- Confirm the key appears in every bundle file you found
- Highlight any TODO stubs that need human translation
- Remind the user that the `Accept-Language` header drives locale resolution

## Output

1. List of bundle files updated (or created)
2. Throw snippet
3. Test snippet
4. Any TODO stubs that need translation
