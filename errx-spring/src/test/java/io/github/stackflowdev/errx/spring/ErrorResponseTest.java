package io.github.stackflowdev.errx.spring;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ErrorResponseTest {

    @Test
    void of_mapsAllFields() {
        ErrorResponse response = ErrorResponse.of(
                "user.not_found",
                "User not found",
                Map.of("user_id", "required")
        );

        assertThat(response.code()).isEqualTo("user.not_found");
        assertThat(response.message()).isEqualTo("User not found");
        assertThat(response.fields()).containsEntry("user_id", "required");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void of_emptyFieldsBecomesNull() {
        ErrorResponse response = ErrorResponse.of("internal", "server error", Map.of());
        assertThat(response.fields()).isNull();
    }

    @Test
    void of_nullFieldsBecomesNull() {
        ErrorResponse response = ErrorResponse.of("internal", "server error", null);
        assertThat(response.fields()).isNull();
    }

    @Test
    void of_nonEmptyFieldsPreserved() {
        var fields = Map.of("email", "invalid format", "name", "required");

        ErrorResponse response = ErrorResponse.of("validation.failed", "validation failed", fields);

        assertThat(response.fields()).hasSize(2);
        assertThat(response.fields()).containsEntry("email", "invalid format");
        assertThat(response.fields()).containsEntry("name", "required");
    }
}
