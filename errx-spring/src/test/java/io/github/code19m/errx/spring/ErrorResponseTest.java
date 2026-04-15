package io.github.code19m.errx.spring;

import io.github.code19m.errx.ErrorType;
import io.github.code19m.errx.ErrorX;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ErrorResponseTest {

    // ── Factory Method ──────────────────────────────────────────────

    @Nested
    class FactoryMethodTests {

        @Test
        void of_mapsAllFields() {
            ErrorResponse response = ErrorResponse.of(
                    "USER_NOT_FOUND",
                    "NOT_FOUND",
                    "user not found",
                    Map.of("user_id", "required")
            );

            assertThat(response.code()).isEqualTo("USER_NOT_FOUND");
            assertThat(response.type()).isEqualTo("NOT_FOUND");
            assertThat(response.message()).isEqualTo("user not found");
            assertThat(response.fields()).containsEntry("user_id", "required");
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        void of_emptyFieldsBecomesNull() {
            ErrorResponse response = ErrorResponse.of(
                    "INTERNAL", "INTERNAL", "server error", Map.of()
            );

            assertThat(response.fields()).isNull();
        }

        @Test
        void of_nullFieldsBecomesNull() {
            ErrorResponse response = ErrorResponse.of(
                    "INTERNAL", "INTERNAL", "server error", null
            );

            assertThat(response.fields()).isNull();
        }

        @Test
        void of_nonEmptyFieldsPreserved() {
            var fields = Map.of("email", "invalid format", "name", "required");

            ErrorResponse response = ErrorResponse.of(
                    "VALIDATION", "VALIDATION", "validation failed", fields
            );

            assertThat(response.fields()).hasSize(2);
            assertThat(response.fields()).containsEntry("email", "invalid format");
            assertThat(response.fields()).containsEntry("name", "required");
        }

        @Test
        void of_mapsCorrectTypeName() {
            for (ErrorType type : ErrorType.values()) {
                ErrorResponse response = ErrorResponse.of(
                        "CODE", type.name(), "msg", null
                );
                assertThat(response.type()).isEqualTo(type.name());
            }
        }
    }
}
