package io.github.code19m.errx.spring;

import io.github.code19m.errx.ErrorType;
import io.github.code19m.errx.ErrorX;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

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
}
