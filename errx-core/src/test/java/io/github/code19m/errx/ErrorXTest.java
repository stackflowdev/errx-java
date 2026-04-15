package io.github.code19m.errx;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ErrorXTest {

    // ── Create ───────────────────────────────────────────────────────

    @Nested
    class CreateTests {

        @Test
        void create_withDefaults() {
            ErrorX ex = ErrorX.create("something failed").build();

            assertThat(ex.getMessage()).isEqualTo("something failed");
            assertThat(ex.code()).isEqualTo(ErrorX.DEFAULT_CODE);
            assertThat(ex.type()).isEqualTo(ErrorX.DEFAULT_TYPE);
            assertThat(ex.fields()).isEmpty();
            assertThat(ex.details()).isEmpty();
            assertThat(ex.trace()).isNotBlank();
        }

        @Test
        void create_withAllOptions() {
            ErrorX ex = ErrorX.create("user not found")
                    .code("USER_NOT_FOUND")
                    .type(ErrorType.NOT_FOUND)
                    .fields(Map.of("user_id", "required"))
                    .details(Map.of("searched_id", "abc-123"))
                    .build();

            assertThat(ex.code()).isEqualTo("USER_NOT_FOUND");
            assertThat(ex.type()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(ex.fields()).containsEntry("user_id", "required");
            assertThat(ex.details()).containsEntry("searched_id", "abc-123");
        }

        @Test
        void createf_formatsMessage() {
            ErrorX ex = ErrorX.createf("User %s not found in %s", "john", "users_table")
                    .build();

            assertThat(ex.getMessage()).isEqualTo("User john not found in users_table");
        }
    }

    // ── Wrap ─────────────────────────────────────────────────────────

    @Nested
    class WrapTests {

        @Test
        void wrap_nullReturnsNull() {
            assertThat(ErrorX.wrap(null)).isNull();
        }

        @Test
        void wrap_plainException() {
            var cause = new RuntimeException("db connection failed");
            ErrorX ex = ErrorX.wrap(cause)
                    .details(Map.of("host", "localhost"))
                    .build();

            assertThat(ex.getMessage()).isEqualTo("db connection failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.code()).isEqualTo(ErrorX.DEFAULT_CODE);
            assertThat(ex.details()).containsEntry("host", "localhost");
        }

        @Test
        void wrap_errorXPreservesMetadata() {
            ErrorX original = ErrorX.create("not found")
                    .code("ORDER_NOT_FOUND")
                    .type(ErrorType.NOT_FOUND)
                    .details(Map.of("order_id", "99"))
                    .build();

            ErrorX wrapped = ErrorX.wrap(original)
                    .details(Map.of("layer", "service"))
                    .build();

            // Code and type carried over from original
            assertThat(wrapped.code()).isEqualTo("ORDER_NOT_FOUND");
            assertThat(wrapped.type()).isEqualTo(ErrorType.NOT_FOUND);

            // Details merged
            assertThat(wrapped.details()).containsEntry("layer", "service");
            assertThat(wrapped.details()).containsEntry("order_id", "99");
        }

        @Test
        void wrap_detailsMergeWithPipeSeparator() {
            ErrorX original = ErrorX.create("err")
                    .details(Map.of("context", "original"))
                    .build();

            ErrorX wrapped = ErrorX.wrap(original)
                    .details(Map.of("context", "wrapper"))
                    .build();

            // New value first, pipe, then old value (same as Go behavior)
            assertThat((String) wrapped.details().get("context"))
                    .isEqualTo("wrapper | original");
        }
    }

    // ── Trace ────────────────────────────────────────────────────────

    @Nested
    class TraceTests {

        @Test
        void trace_containsCallerInfo() {
            ErrorX ex = ErrorX.create("test").build();

            // Should contain this test class and method name
            assertThat(ex.trace()).contains("ErrorXTest");
            assertThat(ex.trace()).contains(".java:");
        }

        @Test
        void trace_chainsOnWrap() {
            ErrorX original = innerMethod();
            ErrorX wrapped = ErrorX.wrap(original).build();

            // Trace should contain arrow separator showing propagation chain
            assertThat(wrapped.trace()).contains("→");
        }

        @Test
        void trace_prefixForCrossService() {
            ErrorX ex = ErrorX.create("remote error")
                    .tracePrefix("order-service")
                    .build();

            assertThat(ex.trace()).startsWith(">>> order-service >>>");
        }

        private ErrorX innerMethod() {
            return ErrorX.create("inner error").build();
        }
    }

    // ── Tools (static utilities) ─────────────────────────────────────

    @Nested
    class ToolsTests {

        @Test
        void getCode_fromErrorX() {
            ErrorX ex = ErrorX.create("e").code("MY_CODE").build();
            assertThat(ErrorX.getCode(ex)).isEqualTo("MY_CODE");
        }

        @Test
        void getCode_fromPlainException() {
            var plain = new RuntimeException("oops");
            assertThat(ErrorX.getCode(plain)).isEqualTo(ErrorX.DEFAULT_CODE);
        }

        @Test
        void getType_fromErrorX() {
            ErrorX ex = ErrorX.create("e").type(ErrorType.FORBIDDEN).build();
            assertThat(ErrorX.getType(ex)).isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        void isCodeIn_matchesCorrectly() {
            ErrorX ex = ErrorX.create("e").code("NOT_FOUND").build();

            assertThat(ErrorX.isCodeIn(ex, "NOT_FOUND", "CONFLICT")).isTrue();
            assertThat(ErrorX.isCodeIn(ex, "INTERNAL")).isFalse();
        }

        @Test
        void asErrorX_convertsPlainException() {
            var plain = new IllegalArgumentException("bad input");
            ErrorX ex = ErrorX.asErrorX(plain);

            assertThat(ex.getMessage()).isEqualTo("bad input");
            assertThat(ex.code()).isEqualTo(ErrorX.DEFAULT_CODE);
            assertThat(ex.type()).isEqualTo(ErrorX.DEFAULT_TYPE);
        }

        @Test
        void asErrorX_returnsExistingErrorX() {
            ErrorX original = ErrorX.create("e").code("X").build();
            ErrorX result = ErrorX.asErrorX(original);

            assertThat(result).isSameAs(original);
        }
    }

    // ── toString ─────────────────────────────────────────────────────

    @Test
    void toString_formatsLikeGo() {
        ErrorX ex = ErrorX.create("user not found")
                .code("USER_NOT_FOUND")
                .type(ErrorType.NOT_FOUND)
                .build();

        assertThat(ex.toString()).isEqualTo("[NOT_FOUND: USER_NOT_FOUND] - user not found");
    }

    // ── Immutability ─────────────────────────────────────────────────

    @Test
    void fieldsAndDetails_areImmutable() {
        ErrorX ex = ErrorX.create("e")
                .fields(Map.of("a", "b"))
                .details(Map.of("x", "y"))
                .build();

        assertThatThrownBy(() -> ex.fields().put("hack", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> ex.details().put("hack", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
