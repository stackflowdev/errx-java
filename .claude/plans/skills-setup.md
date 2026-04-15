# Plan: errx-java uchun Claude Code Skill'lar yaratish

## Status: ✅ COMPLETED

## Context

errx-java loyihasi Go'dagi `errx` package'ning Java port'idir. Hozirda `errx-core` to'liq implement qilingan (ErrorX + ErrorType), `errx-spring` module'da handler bor lekin test'lar yo'q. Yana 4 ta feature implement qilinmagan (gRPC, Bean Validation, WrapWithTypeOnCodes, AutoConfiguration).

Samarali ishlash uchun 3 ta reusable skill yaratdik — bu skill'lar Go→Java porting jarayonini tezlashtiradi va sifatni oshiradi.

---

## Skill 1: `/port-from-go` — ✅ Done

**File:** `.claude/skills/port-from-go.md`

**Nima qiladi:**
- Go file'ni o'qiydi, CLAUDE.md qoidalarini qo'llaydi
- Mavjud Java code'ni tekshiradi — allaqachon port qilingan narsalarni qayta yozmaydi
- Java code + test'larni loyiha pattern'lariga mos yozadi

---

## Skill 2: `/test-feature` — ✅ Done

**File:** `.claude/skills/test-feature.md`

**Nima qiladi:**
- Target class'ni o'qib, barcha public method'larni aniqlaydi
- `@Nested` classes, AssertJ pattern'larda JUnit 5 test'lar yozadi
- Happy path, edge case, null handling, immutability check'larni qamrab oladi

---

## Skill 3: `/check-go-parity` — ✅ Done

**File:** `.claude/skills/check-go-parity.md`

**Nima qiladi:**
- Go source'dagi barcha public export'larni ro'yxatlaydi
- Java'dagi mos implementatsiyalarni topadi
- Hisobot: ✅ ported, ⚠️ partial, ❌ missing

---

## Next: Implement Remaining Features

Still TODO in the project:

- [x] `WrapWithTypeOnCodes` utility method — `errx-core` ga qo'shildi
- [x] Spring module tests — ErrorResponseTest (5 test) + ErrorXHandlerTest (11 test)
- [x] Spring Bean Validation integration — MethodArgumentNotValidException + ConstraintViolationException
- [ ] Spring Boot auto-configuration — `@AutoConfiguration` / `spring.factories`
