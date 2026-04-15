package io.github.stackflowdev.errx.spring;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response body returned to API clients.
 *
 * <p>Fields with null values are excluded from JSON output,
 * so validation-free errors won't have an empty "fields" key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String type,
        String message,
        Map<String, String> fields,
        Instant timestamp
) {

    public static ErrorResponse of(String code, String type, String message, Map<String, String> fields) {
        return new ErrorResponse(
                code,
                type,
                message,
                fields == null || fields.isEmpty() ? null : fields,
                Instant.now()
        );
    }
}
