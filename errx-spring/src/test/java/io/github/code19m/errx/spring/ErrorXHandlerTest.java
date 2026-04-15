package io.github.code19m.errx.spring;

import io.github.code19m.errx.ErrorType;
import io.github.code19m.errx.ErrorX;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorXHandlerTest {

    private final ErrorXHandler handler = new ErrorXHandler();

    // ── HTTP Status Mapping ─────────────────────────────────────────

    @Nested
    class HttpStatusMappingTests {

        @Test
        void handle_internalError_returns500() {
            ErrorX ex = ErrorX.create("server error").type(ErrorType.INTERNAL).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }

        @Test
        void handle_validationError_returns400() {
            ErrorX ex = ErrorX.create("bad input").type(ErrorType.VALIDATION).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void handle_notFoundError_returns400_not404() {
            // CRITICAL: NOT_FOUND maps to 400, not 404
            // 404 is reserved for routing-level "no such endpoint"
            ErrorX ex = ErrorX.create("user not found").type(ErrorType.NOT_FOUND).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void handle_conflictError_returns409() {
            ErrorX ex = ErrorX.create("already exists").type(ErrorType.CONFLICT).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
        }

        @Test
        void handle_authError_returns401() {
            ErrorX ex = ErrorX.create("unauthorized").type(ErrorType.AUTH).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }

        @Test
        void handle_forbiddenError_returns403() {
            ErrorX ex = ErrorX.create("access denied").type(ErrorType.FORBIDDEN).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(403);
        }

        @Test
        void handle_throttlingError_returns429() {
            ErrorX ex = ErrorX.create("too many requests").type(ErrorType.THROTTLING).build();
            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            assertThat(response.getStatusCode().value()).isEqualTo(429);
        }
    }

    // ── Response Body ───────────────────────────────────────────────

    @Nested
    class ResponseBodyTests {

        @Test
        void handle_setsCorrectResponseBody() {
            ErrorX ex = ErrorX.create("something failed")
                    .code("PROCESS_ERROR")
                    .type(ErrorType.INTERNAL)
                    .build();

            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            ErrorResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo("PROCESS_ERROR");
            assertThat(body.type()).isEqualTo("INTERNAL");
            assertThat(body.message()).isEqualTo("something failed");
            assertThat(body.timestamp()).isNotNull();
        }

        @Test
        void handle_includesFieldsWhenPresent() {
            ErrorX ex = ErrorX.create("validation failed")
                    .type(ErrorType.VALIDATION)
                    .fields(Map.of("email", "invalid format", "name", "required"))
                    .build();

            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            ErrorResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("email", "invalid format");
            assertThat(body.fields()).containsEntry("name", "required");
        }

        @Test
        void handle_excludesFieldsWhenEmpty() {
            ErrorX ex = ErrorX.create("not found")
                    .type(ErrorType.NOT_FOUND)
                    .build();

            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            ErrorResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).isNull();
        }

        @Test
        void handle_neverExposesDetailsToClient() {
            ErrorX ex = ErrorX.create("db error")
                    .type(ErrorType.INTERNAL)
                    .details(Map.of("query", "SELECT * FROM users", "host", "db-prod-01"))
                    .build();

            ResponseEntity<ErrorResponse> response = handler.handleErrorX(ex);
            ErrorResponse body = response.getBody();

            // ErrorResponse has no details field — details stay in server logs only
            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo(ErrorX.DEFAULT_CODE);
        }
    }

    // ── MethodArgumentNotValidException (@Valid on @RequestBody) ─────

    @Nested
    class MethodArgumentNotValidTests {

        @Test
        void handle_returnsValidationType400() {
            var ex = createMethodArgumentNotValidException(
                    Map.of("email", "must be a valid email")
            );

            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().type()).isEqualTo("VALIDATION");
            assertThat(response.getBody().code()).isEqualTo("VALIDATION");
        }

        @Test
        void handle_extractsFieldErrors() {
            var ex = createMethodArgumentNotValidException(
                    Map.of("email", "must be a valid email", "name", "must not be blank")
            );

            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);
            ErrorResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("email", "must be a valid email");
            assertThat(body.fields()).containsEntry("name", "must not be blank");
        }

        @Test
        void handle_setsValidationFailedMessage() {
            var ex = createMethodArgumentNotValidException(
                    Map.of("age", "must be at least 18")
            );

            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Validation failed");
        }

        private MethodArgumentNotValidException createMethodArgumentNotValidException(
                Map<String, String> fieldErrors) {
            var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            fieldErrors.forEach((field, message) ->
                    bindingResult.addError(new FieldError("request", field, message))
            );
            return new MethodArgumentNotValidException(
                    mock(MethodParameter.class), bindingResult
            );
        }
    }

    // ── ConstraintViolationException (@Validated on params) ─────────

    @Nested
    class ConstraintViolationTests {

        @Test
        void handle_returnsValidationType400() {
            var ex = createConstraintViolationException(
                    Map.of("userId", "must not be null")
            );

            ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().type()).isEqualTo("VALIDATION");
            assertThat(response.getBody().code()).isEqualTo("VALIDATION");
        }

        @Test
        void handle_extractsViolationFields() {
            var ex = createConstraintViolationException(
                    Map.of("userId", "must not be null", "limit", "must be between 1 and 100")
            );

            ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);
            ErrorResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.fields()).hasSize(2);
            assertThat(body.fields()).containsEntry("userId", "must not be null");
            assertThat(body.fields()).containsEntry("limit", "must be between 1 and 100");
        }

        @Test
        void handle_setsValidationFailedMessage() {
            var ex = createConstraintViolationException(
                    Map.of("id", "must be positive")
            );

            ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Validation failed");
        }

        @SuppressWarnings("unchecked")
        private ConstraintViolationException createConstraintViolationException(
                Map<String, String> violations) {
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
