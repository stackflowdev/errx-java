package io.github.stackflowdev.errx.spring;

import io.github.stackflowdev.errx.ErrorType;
import io.github.stackflowdev.errx.ErrxException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrxExceptionHandlerTest {

    private ErrxExceptionHandler handler;
    private ErrxExceptionHandler handlerWithI18n;

    @BeforeEach
    void setUp() {
        handler = new ErrxExceptionHandler();

        // MessageSource pointing to test-resources/messages_*.properties
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        // Consistent apostrophe escaping: single quotes always treated as MessageFormat
        ms.setAlwaysUseMessageFormat(true);
        handlerWithI18n = new ErrxExceptionHandler(ms);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ── HTTP Status Mapping ─────────────────────────────────────────

    @Nested
    class HttpStatusMappingTests {

        @Test
        void handle_internalError_returns500() {
            var ex = ErrxException.create().type(ErrorType.INTERNAL).message("boom").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(500);
        }

        @Test
        void handle_validationError_returns400() {
            var ex = ErrxException.create().type(ErrorType.VALIDATION).message("bad").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void handle_notFoundError_returns400_not404() {
            // NOT_FOUND → 400 (404 reserved for routing-level errors)
            var ex = ErrxException.create().type(ErrorType.NOT_FOUND).message("nope").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void handle_conflictError_returns409() {
            var ex = ErrxException.create().type(ErrorType.CONFLICT).message("exists").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(409);
        }

        @Test
        void handle_authError_returns401() {
            var ex = ErrxException.create().type(ErrorType.AUTH).message("auth").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(401);
        }

        @Test
        void handle_forbiddenError_returns403() {
            var ex = ErrxException.create().type(ErrorType.FORBIDDEN).message("nope").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(403);
        }

        @Test
        void handle_throttlingError_returns429() {
            var ex = ErrxException.create().type(ErrorType.THROTTLING).message("slow").build();
            assertThat(handler.handleErrxException(ex).getStatusCode().value()).isEqualTo(429);
        }
    }

    // ── Response Body Shape ─────────────────────────────────────────

    @Nested
    class ResponseBodyTests {

        @Test
        void response_neverIncludesTypeField() {
            // Verify the record's component list has no "type" field
            assertThat(ErrorResponse.class.getRecordComponents())
                    .extracting(rc -> rc.getName())
                    .containsExactly("code", "message", "fields", "timestamp");
        }

        @Test
        void handle_setsCodeAndMessage() {
            var ex = ErrxException.create()
                    .code("process.error")
                    .type(ErrorType.INTERNAL)
                    .message("something failed")
                    .build();

            ErrorResponse body = handler.handleErrxException(ex).getBody();

            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo("process.error");
            assertThat(body.message()).isEqualTo("something failed");
            assertThat(body.timestamp()).isNotNull();
        }

        @Test
        void handle_includesFieldsWhenPresent() {
            var ex = ErrxException.create()
                    .type(ErrorType.VALIDATION)
                    .message("validation failed")
                    .fields(Map.of("email", "invalid", "name", "required"))
                    .build();

            ErrorResponse body = handler.handleErrxException(ex).getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("email", "invalid");
        }

        @Test
        void handle_excludesFieldsWhenEmpty() {
            var ex = ErrxException.create().type(ErrorType.NOT_FOUND).message("nope").build();

            ErrorResponse body = handler.handleErrxException(ex).getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).isNull();
        }

        @Test
        void handle_neverExposesDetailsToClient() {
            var ex = ErrxException.create()
                    .type(ErrorType.INTERNAL)
                    .message("db error")
                    .details(Map.of("query", "SELECT *", "host", "db-01"))
                    .build();

            ErrorResponse body = handler.handleErrxException(ex).getBody();

            assertThat(body).isNotNull();
            // ErrorResponse has no 'details' component; verify that structurally
            assertThat(ErrorResponse.class.getRecordComponents())
                    .extracting(rc -> rc.getName())
                    .doesNotContain("details");
        }
    }

    // ── i18n Resolution ─────────────────────────────────────────────

    @Nested
    class I18nTests {

        @Test
        void noMessageSource_fallsBackToCode() {
            var ex = ErrxException.create().code("user.not_found").type(ErrorType.NOT_FOUND).build();

            String message = handler.handleErrxException(ex).getBody().message();

            assertThat(message).isEqualTo("user.not_found");
        }

        @Test
        void withMessageSource_resolvesInEnglish() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            var ex = ErrxException.create().code("user.not_found").type(ErrorType.NOT_FOUND).args(42).build();

            String message = handlerWithI18n.handleErrxException(ex).getBody().message();

            assertThat(message).isEqualTo("User not found (ID: 42)");
        }

        @Test
        void withMessageSource_resolvesInUzbek() {
            LocaleContextHolder.setLocale(new Locale("uz"));
            var ex = ErrxException.create().code("user.not_found").type(ErrorType.NOT_FOUND).args(42).build();

            String message = handlerWithI18n.handleErrxException(ex).getBody().message();

            assertThat(message).isEqualTo("Foydalanuvchi topilmadi (ID: 42)");
        }

        @Test
        void explicitMessage_bypassesBundleLookup() {
            LocaleContextHolder.setLocale(new Locale("uz"));
            var ex = ErrxException.create()
                    .code("user.not_found")
                    .message("hardcoded message")
                    .type(ErrorType.NOT_FOUND)
                    .build();

            String message = handlerWithI18n.handleErrxException(ex).getBody().message();

            assertThat(message).isEqualTo("hardcoded message");
        }

        @Test
        void unknownCode_fallsBackToCode() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            var ex = ErrxException.create().code("made.up.code").type(ErrorType.INTERNAL).build();

            String message = handlerWithI18n.handleErrxException(ex).getBody().message();

            assertThat(message).isEqualTo("made.up.code");
        }
    }

    // ── MethodArgumentNotValidException ─────────────────────────────

    @Nested
    class MethodArgumentNotValidTests {

        @Test
        void handle_returns400() {
            var ex = newMethodArgumentNotValidException(Map.of("email", "must be a valid email"));

            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().code()).isEqualTo("validation.failed");
        }

        @Test
        void handle_extractsFieldErrors() {
            var ex = newMethodArgumentNotValidException(Map.of(
                    "email", "must be a valid email",
                    "name", "must not be blank"
            ));

            ErrorResponse body = handler.handleMethodArgumentNotValid(ex).getBody();

            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("email", "must be a valid email");
            assertThat(body.fields()).containsEntry("name", "must not be blank");
        }

        @Test
        void handle_usesI18nMessageWhenAvailable() {
            LocaleContextHolder.setLocale(new Locale("uz"));
            var ex = newMethodArgumentNotValidException(Map.of("x", "bad"));

            ErrorResponse body = handlerWithI18n.handleMethodArgumentNotValid(ex).getBody();

            assertThat(body.message()).isEqualTo("Ma'lumotlar tekshiruvidan o'tmadi");
        }

        @Test
        void handle_fallsBackWhenNoMessageSource() {
            var ex = newMethodArgumentNotValidException(Map.of("x", "bad"));

            ErrorResponse body = handler.handleMethodArgumentNotValid(ex).getBody();

            assertThat(body.message()).isEqualTo("Validation failed");
        }

        private MethodArgumentNotValidException newMethodArgumentNotValidException(
                Map<String, String> fieldErrors) {
            var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            fieldErrors.forEach((field, message) ->
                    bindingResult.addError(new FieldError("request", field, message))
            );
            return new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
        }
    }

    // ── ConstraintViolationException ────────────────────────────────

    @Nested
    class ConstraintViolationTests {

        @Test
        void handle_returns400() {
            var ex = newConstraintViolationException(Map.of("userId", "must not be null"));

            ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().code()).isEqualTo("validation.failed");
        }

        @Test
        void handle_extractsViolationFields() {
            var ex = newConstraintViolationException(Map.of(
                    "userId", "must not be null",
                    "limit", "must be between 1 and 100"
            ));

            ErrorResponse body = handler.handleConstraintViolation(ex).getBody();

            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("userId", "must not be null");
        }

        @SuppressWarnings("unchecked")
        private ConstraintViolationException newConstraintViolationException(Map<String, String> violations) {
            Set<ConstraintViolation<?>> violationSet = violations.entrySet().stream()
                    .map(entry -> {
                        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
                        Path path = mock(Path.class);
                        when(path.toString()).thenReturn(entry.getKey());
                        when(cv.getPropertyPath()).thenReturn(path);
                        when(cv.getMessage()).thenReturn(entry.getValue());
                        return (ConstraintViolation<?>) cv;
                    })
                    .collect(java.util.stream.Collectors.toSet());
            return new ConstraintViolationException(violationSet);
        }
    }
}
