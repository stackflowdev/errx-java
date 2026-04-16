package io.github.stackflowdev.errx;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ErrxExceptionTest {

    // ── Create ───────────────────────────────────────────────────────

    @Nested
    class CreateTests {

        @Test
        void create_withDefaults() {
            ErrxException ex = ErrxException.create().build();

            assertThat(ex.code()).isEqualTo(ErrxException.DEFAULT_CODE);
            assertThat(ex.type()).isEqualTo(ErrxException.DEFAULT_TYPE);
            assertThat(ex.args()).isEmpty();
            assertThat(ex.fields()).isEmpty();
            assertThat(ex.details()).isEmpty();
            assertThat(ex.hasExplicitMessage()).isFalse();
            assertThat(ex.getMessage()).isNull();
        }

        @Test
        void create_withAllOptions() {
            ErrxException ex = ErrxException.create()
                    .code("user.not_found")
                    .type(ErrorType.NOT_FOUND)
                    .args(42, "alice")
                    .fields(Map.of("user_id", "required"))
                    .details(Map.of("searched_id", "abc-123"))
                    .build();

            assertThat(ex.code()).isEqualTo("user.not_found");
            assertThat(ex.type()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(ex.args()).containsExactly(42, "alice");
            assertThat(ex.fields()).containsEntry("user_id", "required");
            assertThat(ex.details()).containsEntry("searched_id", "abc-123");
        }

        @Test
        void create_withExplicitMessage() {
            ErrxException ex = ErrxException.create()
                    .code("db.error")
                    .message("Connection timed out")
                    .build();

            assertThat(ex.getMessage()).isEqualTo("Connection timed out");
            assertThat(ex.hasExplicitMessage()).isTrue();
        }
    }

    // ── Args ─────────────────────────────────────────────────────────

    @Nested
    class ArgsTests {

        @Test
        void args_varargsStored() {
            ErrxException ex = ErrxException.create().args(1, "two", 3.0).build();

            assertThat(ex.args()).containsExactly(1, "two", 3.0);
        }

        @Test
        void args_nullBecomesEmptyArray() {
            ErrxException ex = ErrxException.create().args((Object[]) null).build();

            assertThat(ex.args()).isEmpty();
        }

        @Test
        void args_emptyVarargs() {
            ErrxException ex = ErrxException.create().args().build();

            assertThat(ex.args()).isEmpty();
        }
    }

    // ── Wrap ─────────────────────────────────────────────────────────

    @Nested
    class WrapTests {

        @Test
        void wrap_nullReturnsNull() {
            assertThat(ErrxException.wrap(null)).isNull();
        }

        @Test
        void wrap_plainException() {
            var cause = new RuntimeException("db connection failed");
            ErrxException ex = ErrxException.wrap(cause)
                    .details(Map.of("host", "localhost"))
                    .build();

            assertThat(ex.getMessage()).isEqualTo("db connection failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.code()).isEqualTo(ErrxException.DEFAULT_CODE);
            assertThat(ex.details()).containsEntry("host", "localhost");
            // Plain exception message is treated as explicit — no bundle lookup
            assertThat(ex.hasExplicitMessage()).isTrue();
        }

        @Test
        void wrap_errxExceptionPreservesMetadata() {
            ErrxException original = ErrxException.create()
                    .code("order.not_found")
                    .type(ErrorType.NOT_FOUND)
                    .args(99)
                    .details(Map.of("order_id", "99"))
                    .build();

            ErrxException wrapped = ErrxException.wrap(original)
                    .details(Map.of("layer", "service"))
                    .build();

            assertThat(wrapped.code()).isEqualTo("order.not_found");
            assertThat(wrapped.type()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(wrapped.args()).containsExactly(99);
            assertThat(wrapped.details()).containsEntry("layer", "service");
            assertThat(wrapped.details()).containsEntry("order_id", "99");
        }

        @Test
        void wrap_detailsMergeWithPipeSeparator() {
            ErrxException original = ErrxException.create()
                    .details(Map.of("context", "original"))
                    .build();

            ErrxException wrapped = ErrxException.wrap(original)
                    .details(Map.of("context", "wrapper"))
                    .build();

            assertThat((String) wrapped.details().get("context"))
                    .isEqualTo("wrapper | original");
        }

        @Test
        void wrap_preservesCauseChain() {
            var root = new IllegalStateException("root cause");
            var middle = new RuntimeException("middle", root);
            ErrxException ex = ErrxException.wrap(middle).code("boom").build();

            assertThat(ex.getCause()).isSameAs(middle);
            assertThat(ex.getCause().getCause()).isSameAs(root);
        }
    }

    // ── Tools (static utilities) ─────────────────────────────────────

    @Nested
    class ToolsTests {

        @Test
        void getCode_fromErrxException() {
            ErrxException ex = ErrxException.create().code("my.code").build();
            assertThat(ErrxException.getCode(ex)).isEqualTo("my.code");
        }

        @Test
        void getCode_fromPlainException() {
            var plain = new RuntimeException("oops");
            assertThat(ErrxException.getCode(plain)).isEqualTo(ErrxException.DEFAULT_CODE);
        }

        @Test
        void getType_fromErrxException() {
            ErrxException ex = ErrxException.create().type(ErrorType.FORBIDDEN).build();
            assertThat(ErrxException.getType(ex)).isEqualTo(ErrorType.FORBIDDEN);
        }

        @Test
        void isCodeIn_matchesCorrectly() {
            ErrxException ex = ErrxException.create().code("not_found").build();

            assertThat(ErrxException.isCodeIn(ex, "not_found", "conflict")).isTrue();
            assertThat(ErrxException.isCodeIn(ex, "internal")).isFalse();
        }

        @Test
        void asErrxException_convertsPlainException() {
            var plain = new IllegalArgumentException("bad input");
            ErrxException ex = ErrxException.asErrxException(plain);

            assertThat(ex.getMessage()).isEqualTo("bad input");
            assertThat(ex.code()).isEqualTo(ErrxException.DEFAULT_CODE);
            assertThat(ex.type()).isEqualTo(ErrxException.DEFAULT_TYPE);
        }

        @Test
        void asErrxException_returnsExisting() {
            ErrxException original = ErrxException.create().code("x").build();
            ErrxException result = ErrxException.asErrxException(original);

            assertThat(result).isSameAs(original);
        }

        @Test
        void wrapWithTypeOnCodes_nullReturnsNull() {
            assertThat(ErrxException.wrapWithTypeOnCodes(null, ErrorType.VALIDATION, "x")).isNull();
        }

        @Test
        void wrapWithTypeOnCodes_changesTypeWhenCodeMatches() {
            ErrxException original = ErrxException.create()
                    .code("invalid.email")
                    .type(ErrorType.INTERNAL)
                    .build();

            ErrxException result = ErrxException.wrapWithTypeOnCodes(
                    original, ErrorType.VALIDATION, "invalid.email", "invalid.phone");

            assertThat(result.type()).isEqualTo(ErrorType.VALIDATION);
            assertThat(result.code()).isEqualTo("invalid.email");
        }

        @Test
        void wrapWithTypeOnCodes_preservesTypeWhenCodeDoesNotMatch() {
            ErrxException original = ErrxException.create()
                    .code("db.failure")
                    .type(ErrorType.INTERNAL)
                    .build();

            ErrxException result = ErrxException.wrapWithTypeOnCodes(
                    original, ErrorType.VALIDATION, "invalid.email");

            assertThat(result.type()).isEqualTo(ErrorType.INTERNAL);
        }

        @Test
        void wrapWithTypeOnCodes_preservesMetadata() {
            ErrxException original = ErrxException.create()
                    .code("my.code")
                    .args("x")
                    .fields(Map.of("name", "required"))
                    .details(Map.of("attempt", 3))
                    .build();

            ErrxException result = ErrxException.wrapWithTypeOnCodes(
                    original, ErrorType.VALIDATION, "other");

            assertThat(result.code()).isEqualTo("my.code");
            assertThat(result.args()).containsExactly("x");
            assertThat(result.fields()).containsEntry("name", "required");
            assertThat(result.details()).containsEntry("attempt", 3);
        }
    }

    // ── toString ─────────────────────────────────────────────────────

    @Test
    void toString_formatsConsistently() {
        ErrxException ex = ErrxException.create()
                .code("user.not_found")
                .type(ErrorType.NOT_FOUND)
                .message("User not found")
                .build();

        assertThat(ex.toString()).isEqualTo("[NOT_FOUND: user.not_found] - User not found");
    }

    // ── Null Safety ──────────────────────────────────────────────────

    @Nested
    class NullSafetyTests {

        @Test
        void fields_nullIsIgnored() {
            ErrxException ex = ErrxException.create().fields(null).build();
            assertThat(ex.fields()).isEmpty();
        }

        @Test
        void details_nullIsIgnored() {
            ErrxException ex = ErrxException.create().details(null).build();
            assertThat(ex.details()).isEmpty();
        }
    }

    // ── Immutability ─────────────────────────────────────────────────

    @Test
    void fieldsAndDetails_areImmutable() {
        ErrxException ex = ErrxException.create()
                .fields(Map.of("a", "b"))
                .details(Map.of("x", "y"))
                .build();

        assertThatThrownBy(() -> ex.fields().put("hack", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> ex.details().put("hack", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
