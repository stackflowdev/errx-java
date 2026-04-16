# Plan: Java-native redesign — v0.2.0

## Status
COMPLETED — barcha 4 bosqich bajarildi, build yashil, v0.2.0 tayyor

## Context

errx-java hozirgacha Go'dan port qilingan edi. **v0.2.0 dan boshlab loyiha Go versiyaga bog'liq emas** — sof Java best practice'lar asosida mustaqil kutubxona. Go parity talabidan voz kechamiz, Go havolalari hujjatlardan olib tashlanadi. Quyidagi 4 ta qaror shu yo'nalishda.

---

## Qaror 1: Java best practice — custom `trace`dan voz kechamiz

### Java'da exception tracing bo'yicha standart yondashuv
1. **Stack trace avtomatik** — JVM har bir `Throwable` uchun to'liq stack trace saqlaydi
2. **Cause chain** — `new BusinessException("msg", originalEx)` → `getCause()` orqali traverse
3. **SLF4J logging** — `log.error("something failed", ex)` avtomatik ravishda to'liq trace + cause chain'ni chiqaradi
4. **Custom trace string'lar — atipik** — Spring, Hibernate, AWS SDK va boshqa katta framework'lar o'z "trace" string'ini qurmaydi. Ular Java'ning built-in mexanizmlariga tayanadi.

### Qaror: `trace`, `tracePrefix`, `buildTrace`, `StackWalker` — butunlay olib tashlanadi
- `trace` field → **yo'q**
- `tracePrefix()` builder method → **yo'q**
- `StackWalker` logic → **yo'q**
- Stack trace kerak bo'lganda → `ex.getStackTrace()` (Java API) yoki `log.error("msg", ex)`
- Cause chain kerak bo'lganda → `ex.getCause()` (avtomatik, `super(message, cause)` Java'da)

### Cross-service propagation (ilgari `tracePrefix` edi)
- Agar foydalanuvchi service nomini saqlamoqchi bo'lsa — `details`ga `"source", "order-service"` qo'yadi
- Yoki javadoc'da "details ichida source key ishlatishni tavsiya qilamiz" yoziladi
- Alohida field qo'shish kerak emas — minimalizm

### `wrap()` saqlab qolinadi (lekin qayta ishlanadi)
`wrap()`ning Java'da qimmat qadri:
1. **Metadata propagation** — nested `ErrxException`dan `code`, `type`, `fields` o'tadi
2. **Details merging** — duplicate key'da `"new | old"` concat
3. **Selective override** — builder orqali istalgan maydonni o'zgartirish
4. **Cause chain avtomatik** — `super(msg, cause)` Java'ning o'zi qo'llab-quvvatlaydi

---

## Qaror 2: Rename `ErrorX` → `ErrxException`

- **Sabab:** Java konvensiyasi — exception'lar `*Exception` suffix bilan nomlanadi (`IOException`, `IllegalStateException`, `ResponseStatusException`)
- `ErrorX` nomi `java.lang.Error` (JVM fatal errors) bilan chalkashadi
- Brand `errx` paket nomida va class prefiksida saqlanadi
- Static factorylar: `ErrxException.create(...)`, `ErrxException.wrap(...)`, `ErrxException.asErrxException(...)` va h.k.
- Barcha yordamchi fayllar ham qayta nomlanadi:
  - `ErrorXHandler` → `ErrxExceptionHandler`
  - `ErrorXAutoConfiguration` → `ErrxExceptionAutoConfiguration`
  - `ErrorXTest` → `ErrxExceptionTest`
  - `ErrorXHandlerTest` → `ErrxExceptionHandlerTest`
- `ErrorType` enum nomi saqlanadi (u allaqachon Java'cha ko'rinadi)
- `ErrorResponse` record nomi ham saqlanadi (DTO, exception emas)

---

## Qaror 3: i18n — `code` == message key

### Asosiy g'oya
Error `code` **ham machine-readable kod, ham i18n message key**. Ikki ta field'ni bog'lamaslik — bitta field ikki vazifa bajaradi.

### Foydalanuvchi API
```java
throw ErrxException.create()
    .code("user.not_found")           // ← kod VA bundle key
    .args(userId)                     // ← {0}, {1}, ... uchun
    .type(ErrorType.NOT_FOUND)
    .build();
```

### Resource bundle'lar

**`messages_uz.properties`:**
```properties
user.not_found=Foydalanuvchi topilmadi (ID: {0})
order.invalid=Buyurtma noto'g'ri
validation.failed=Ma'lumotlar tekshiruvidan o'tmadi
```

**`messages_en.properties`:**
```properties
user.not_found=User not found (ID: {0})
order.invalid=Invalid order
validation.failed=Validation failed
```

### Response flow
1. Client: `GET /api/users/999` — header `Accept-Language: uz`
2. Server: `throw ErrxException.create().code("user.not_found").args(999).build();`
3. `ErrxExceptionHandler`:
   - `Locale locale = LocaleContextHolder.getLocale()` → `uz` (Spring avtomatik `Accept-Language`ni o'qiydi)
   - `String message = messageSource.getMessage(ex.code(), ex.args(), fallback, locale)`
   - Natija: `"Foydalanuvchi topilmadi (ID: 999)"`
4. Response'da resolved message.

### `errx-core` (zero-deps)
- `ErrxException`ga yangi field: `Object[] args`
- Builder: `.args(Object...)` method
- `MessageSource` haqida hech narsa bilmaydi — faqat ma'lumot saqlaydi
- Explicit `message` ham qo'yish mumkin (raw message — i18n o'tkazib yuboriladi)

### `errx-spring`
- `ErrxExceptionHandler` `MessageSource`'ni inject qiladi (Spring'da avtomatik bean)
- Resolution chain:
  1. Agar foydalanuvchi explicit `.message("raw")` bergan bo'lsa → uni qaytaradi, bundle'ga murojaat qilmaydi
  2. Aks holda → `messageSource.getMessage(code, args, defaultMessage, locale)`
  3. Bundle'da key topilmasa → Spring'ning `defaultMessage` fallback'i (biz `code`ni fallback qilib beramiz)
- `MessageSource` bean bo'lmasa (foydalanuvchi `messages.properties`ni yaratmasa) — fallback faqat `code` yoki raw message
- `LocaleContextHolder` — Spring Boot avtomatik `AcceptHeaderLocaleResolver` qo'shadi

### Validation xatolari uchun
`MethodArgumentNotValidException` ishlov berishda ham shu logika:
- Field message'lar i18n bundle'dan kelishi mumkin (masalan `@NotBlank` message'i `{jakarta.validation.constraints.NotBlank.message}`)
- Spring allaqachon `MessageSource` orqali validation message'larni resolve qiladi
- Biz faqat validation container message'ini (`"validation.failed"`) resolve qilamiz

---

## Qaror 4: HTTP response'dan `type` ni olib tashlash

### Hozirgi response
```json
{ "code": "USER_NOT_FOUND", "type": "NOT_FOUND", "message": "...", "timestamp": "..." }
```

### Yangi response
```json
{ "code": "user.not_found", "message": "Foydalanuvchi topilmadi", "timestamp": "..." }
```

Validation bilan:
```json
{
  "code": "validation.failed",
  "message": "Ma'lumotlar tekshiruvidan o'tmadi",
  "fields": { "email": "...", "name": "..." },
  "timestamp": "..."
}
```

### Sabab
- `type` har doim HTTP status'ga 1:1 map bo'ladi → client uchun redundant
- Client faqat `code` + HTTP status bilan ishlaydi
- `type` server-side'da qoladi (HTTP status derivation + logging)

### O'zgarishlar
- `ErrorResponse` record'dan `type` olib tashlanadi
- `ErrxException.type()` — ichki API, client ko'rmaydi
- Test'lar: JSON'da `type` yo'qligini verify qilish

---

## Implementation bosqichlari

### Bosqich A: Core refactor (`errx-core`)
1. `ErrorX` → `ErrxException` rename (fayl, class, static metodlar)
2. `trace`, `tracePrefix`, `buildTrace()`, `StackWalker` logic — butunlay olib tashlash
3. Yangi field: `Object[] args` (message format args uchun)
4. Builder: `.args(Object...)` method qo'shish, `.tracePrefix()` olib tashlash
5. `wrap()` saqlanadi, lekin `existingTrace` logic olib tashlanadi (cause chain Java'ning o'zida)
6. `message` — optional, builder'da `.message(String)` orqali explicit qo'yish mumkin
7. Test'lar: trace test'larini olib tashlash, args va i18n integration (core'da args store qilinishini) test qilish

### Bosqich B: Spring refactor (`errx-spring`)
1. Barcha fayllarni rename (`ErrorX*` → `ErrxException*`)
2. `ErrxExceptionHandler`ga `MessageSource` inject qilish (constructor injection)
3. Message resolution: `messageSource.getMessage(ex.code(), ex.args(), ex.getMessage(), locale)`
4. `ErrorResponse` record'dan `type` field olib tashlash
5. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` ichidagi class nomi yangilanadi
6. Validation handler ham i18n'dan foydalansin
7. Test'lar: uz/en bundle'lar bilan scenario, type yo'qligini verify

### Bosqich C: Skills cleanup + new skill
1. `.claude/skills/port-from-go.md` — **o'chirish**
2. `.claude/skills/check-go-parity.md` — **o'chirish**
3. `.claude/skills/test-feature.md` — saqlanadi
4. **Yangi skill: `.claude/skills/add-error-code.md`** — `/add-error-code` flow'ni avtomatlashtiradi (quyida batafsil)

### Bosqich D: Documentation
1. `.claude/CLAUDE.md` — Go havolalari olib tashlanadi, yangi design yoziladi
2. `README.md` — to'liq qayta yoziladi, Go havolalari yo'q, i18n section qo'shiladi
3. `CHANGELOG.md` — yangi fayl, v0.1.0 → v0.2.0 migration guide
4. `errx-spring/src/main/resources/messages.properties` — default bundle (ixtiyoriy)

---

## Yangi skill: `/add-error-code`

**Maqsad:** Yangi error code qo'shish flow'ni avtomatlashtirish — foydalanuvchi bir joydan, skill esa barcha kerakli joylarda yangilash qiladi.

**Input:** `code` (masalan `order.already_paid`), `ErrorType`, args ro'yxati, tavsif

**Skill nima qiladi:**
1. Foydalanuvchidan tasdiq so'raydi: `code`, `type`, `args`
2. Loyiha ichidagi `messages_*.properties` fayllarni topadi (yoki yaratadi agar yo'q bo'lsa)
3. Har bir til bundle'iga yangi key qo'shadi:
   - `messages_en.properties` — majburiy (default)
   - `messages_uz.properties` — agar loyiha uzbek tilini qo'llasa
   - Boshqa bundle'lar — stub (foydalanuvchi keyin to'ldiradi)
4. Namuna controller kodini ko'rsatadi (qanday `throw ErrxException.create()...`)
5. JUnit test namunasini ko'rsatadi

**Fayl o'rni:** `.claude/skills/add-error-code.md`

---

## Critical Files

| Fayl | O'zgarish turi | Izoh |
|------|----------------|------|
| `errx-core/.../ErrorX.java` | Rename → `ErrxException.java` | `trace`, `StackWalker` olib tashlash, `args` qo'shish |
| `errx-core/.../ErrorXTest.java` | Rename → `ErrxExceptionTest.java` | trace test'lar olib tashlash, args test qo'shish |
| `errx-core/.../ErrorType.java` | Saqlanadi | O'zgarishsiz |
| `errx-spring/.../ErrorResponse.java` | Modify | `type` field olib tashlanadi |
| `errx-spring/.../ErrorXHandler.java` | Rename → `ErrxExceptionHandler.java` | `MessageSource` injection, i18n resolution |
| `errx-spring/.../ErrorXAutoConfiguration.java` | Rename → `ErrxExceptionAutoConfiguration.java` | Handler'ga `MessageSource` pass qilish |
| `errx-spring/.../ErrorXHandlerTest.java` | Rename + yangi test'lar | i18n scenariolari (uz/en), type yo'qligi |
| `errx-spring/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | Modify | Yangi class nomi |
| `errx-spring/src/test/resources/messages_en.properties` | Create | Test uchun en bundle |
| `errx-spring/src/test/resources/messages_uz.properties` | Create | Test uchun uz bundle |
| `.claude/CLAUDE.md` | Full rewrite | Go reference bo'limlari olib tashlanadi |
| `.claude/skills/port-from-go.md` | **Delete** | Go source endi relevant emas |
| `.claude/skills/check-go-parity.md` | **Delete** | Parity muhim emas |
| `.claude/skills/test-feature.md` | Saqlanadi | JUnit test generation foydali |
| `.claude/skills/add-error-code.md` | **Create** | Yangi skill |
| `README.md` | Full rewrite | Go havolalari yo'q, i18n section qo'shiladi |
| `CHANGELOG.md` | Create | v0.1.0 → v0.2.0 migration guide |

---

## Verification

1. `./gradlew build` — barcha modullar yashil
2. Yangi test loyiha'da:
   - `curl -H "Accept-Language: uz" localhost:8080/api/users/999` → `"Foydalanuvchi topilmadi (ID: 999)"`
   - `curl -H "Accept-Language: en" localhost:8080/api/users/999` → `"User not found (ID: 999)"`
   - Response JSON'da `type` field yo'q
3. `ErrxException.wrap(sqlException).code("db.error").build()` — metadata propagation ishlaydi (trace yo'q, cause chain bor)
4. Log'da to'liq Java stack trace + cause chain chiqadi (`log.error("err", ex)`)
5. `MessageSource` bean bo'lmagan holatda ham fallback ishlaydi (code yoki raw message)
6. `.claude/skills/`da faqat 2 ta fayl: `test-feature.md` va `add-error-code.md`
7. `CLAUDE.md`da Go havolalari yo'q
