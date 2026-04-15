package io.github.code19m.errx;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured application error with code, type, trace, fields, and details.
 *
 * <p>This is the Java port of the Go {@code errx} package. It extends
 * {@link RuntimeException} so it can be thrown/caught naturally in Java,
 * while carrying the same rich metadata as the Go version.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Create a new error
 * throw ErrorX.create("User not found")
 *              .code("USER_NOT_FOUND")
 *              .type(ErrorType.NOT_FOUND)
 *              .details(Map.of("user_id", userId))
 *              .build();
 *
 * // Wrap an existing exception
 * throw ErrorX.wrap(sqlException)
 *              .code("DB_QUERY_FAILED")
 *              .details(Map.of("query", sql))
 *              .build();
 * }</pre>
 */
public class ErrorX extends RuntimeException {

    // ── Constants ────────────────────────────────────────────────────

    /** Default code when none is specified. */
    public static final String DEFAULT_CODE = "UNSPECIFIED";

    /** Default error type when none is specified. */
    public static final ErrorType DEFAULT_TYPE = ErrorType.INTERNAL;

    /** Separator between trace entries (mirrors Go's arrow). */
    private static final String TRACE_SEPARATOR = " → ";

    // ── Fields ───────────────────────────────────────────────────────

    private final String code;
    private final ErrorType type;
    private final Map<String, String> fields;
    private final Map<String, Object> details;
    private final String trace;

    // ── Constructor (private — use builder) ──────────────────────────

    private ErrorX(Builder builder) {
        super(builder.message, builder.cause);
        this.code = builder.code;
        this.type = builder.type;
        this.fields = Collections.unmodifiableMap(builder.fields);
        this.details = Collections.unmodifiableMap(builder.details);
        this.trace = builder.buildTrace();
    }

    // ── Accessors ────────────────────────────────────────────────────

    /** Machine-readable error code (e.g. "USER_NOT_FOUND"). */
    public String code() {
        return code;
    }

    /** Error category. */
    public ErrorType type() {
        return type;
    }

    /** Validation field errors. Keys = field names, values = error descriptions. */
    public Map<String, String> fields() {
        return fields;
    }

    /** Additional debugging metadata. */
    public Map<String, Object> details() {
        return details;
    }

    /**
     * Custom propagation trace showing where the error was created/wrapped.
     * Format: {@code [File.java:42] Class.method → [Other.java:18] Other.call}
     */
    public String trace() {
        return trace;
    }

    // ── Formatted output ─────────────────────────────────────────────

    /**
     * Returns a formatted string: {@code [TYPE: CODE] - message}.
     * Mirrors Go's {@code Error()} output format.
     */
    @Override
    public String toString() {
        return String.format("[%s: %s] - %s", type.name(), code, getMessage());
    }

    // ── Static factory methods (entry points) ────────────────────────

    /**
     * Start building a new error with a message.
     * Equivalent to Go's {@code errx.New(msg, opts...)}.
     */
    public static Builder create(String message) {
        return new Builder(message, null);
    }

    /**
     * Start building a new error with a formatted message.
     * Equivalent to Go's {@code errx.Newf(format, args...)}.
     */
    public static Builder createf(String format, Object... args) {
        return new Builder(String.format(format, args), null);
    }

    /**
     * Wrap an existing throwable, preserving its message and cause chain.
     * Equivalent to Go's {@code errx.Wrap(err, opts...)}.
     *
     * <p>If the throwable is already an {@link ErrorX}, its metadata
     * (code, type, fields, details, trace) is carried over and can
     * be selectively overridden via the builder.
     *
     * @return builder pre-populated with the original error's data,
     *         or {@code null} if cause is null (matches Go's nil-safe behavior)
     */
    public static Builder wrap(Throwable cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof ErrorX ex) {
            // APPROACH: Carry over existing metadata, allow overrides
            Builder b = new Builder(ex.getMessage(), ex.getCause());
            b.code = ex.code;
            b.type = ex.type;
            b.fields = new HashMap<>(ex.fields);
            b.details = new HashMap<>(ex.details);
            b.existingTrace = ex.trace;
            return b;
        }
        return new Builder(cause.getMessage(), cause);
    }

    // ── Static utility methods (tools) ───────────────────────────────

    /**
     * Extract the error code from any throwable.
     * Returns {@link #DEFAULT_CODE} if not an ErrorX.
     */
    public static String getCode(Throwable t) {
        if (t instanceof ErrorX ex) {
            return ex.code();
        }
        return DEFAULT_CODE;
    }

    /**
     * Extract the error type from any throwable.
     * Returns {@link #DEFAULT_TYPE} if not an ErrorX.
     */
    public static ErrorType getType(Throwable t) {
        if (t instanceof ErrorX ex) {
            return ex.type();
        }
        return DEFAULT_TYPE;
    }

    /**
     * Check if the error's code matches any of the given codes.
     */
    public static boolean isCodeIn(Throwable t, String... codes) {
        String code = getCode(t);
        return Arrays.asList(codes).contains(code);
    }

    /**
     * Convert any throwable to ErrorX.
     * If already an ErrorX, returns as-is. Otherwise wraps with defaults.
     *
     * @throws NullPointerException if t is null
     */
    public static ErrorX asErrorX(Throwable t) {
        Objects.requireNonNull(t, "Cannot convert null to ErrorX");
        if (t instanceof ErrorX ex) {
            return ex;
        }
        return ErrorX.wrap(t).build();
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static class Builder {

        private final String message;
        private final Throwable cause;

        private String code = DEFAULT_CODE;
        private ErrorType type = DEFAULT_TYPE;
        private Map<String, String> fields = new HashMap<>();
        private Map<String, Object> details = new HashMap<>();
        private String existingTrace = null;
        private String tracePrefix = null;

        private Builder(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
        }

        /** Set machine-readable error code. */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /** Set error type. */
        public Builder type(ErrorType type) {
            this.type = type;
            return this;
        }

        /** Set validation field errors. Replaces any existing fields. */
        public Builder fields(Map<String, String> fields) {
            this.fields = new HashMap<>(fields);
            return this;
        }

        /**
         * Add debugging details. Merges with existing details.
         * If a key already exists and both values are strings,
         * they are concatenated with " | " separator.
         */
        public Builder details(Map<String, Object> details) {
            for (var entry : details.entrySet()) {
                String key = entry.getKey();
                Object newVal = entry.getValue();
                Object existingVal = this.details.get(key);

                if (existingVal instanceof String es && newVal instanceof String ns) {
                    // APPROACH: Same merge logic as Go — new value first, pipe separator
                    this.details.put(key, ns + " | " + es);
                } else {
                    this.details.put(key, newVal);
                }
            }
            return this;
        }

        /**
         * Add a trace prefix for cross-service propagation (e.g. gRPC).
         * Prepends {@code ">>> prefix >>>"} to the trace string,
         * and namespaces detail keys with the prefix.
         */
        public Builder tracePrefix(String prefix) {
            this.tracePrefix = prefix;
            return this;
        }

        /** Build the ErrorX, capturing the caller's location in the trace. */
        public ErrorX build() {
            return new ErrorX(this);
        }

        // ── Trace construction (internal) ────────────────────────────

        /**
         * Captures the caller frame and builds the full trace string.
         * Uses StackWalker (Java 9+) for efficient single-frame capture.
         */
        String buildTrace() {
            // GOAL: Capture the frame that called .build(), skipping ErrorX internals
            String callerInfo = StackWalker.getInstance().walk(frames ->
                    frames.filter(f -> !f.getClassName().equals(ErrorX.class.getName()))
                            .filter(f -> !f.getClassName().equals(Builder.class.getName()))
                            .findFirst()
                            .map(f -> String.format("[%s:%d] %s.%s",
                                    f.getFileName(),
                                    f.getLineNumber(),
                                    simpleClassName(f.getClassName()),
                                    f.getMethodName()))
                            .orElse("[unknown]")
            );

            // Chain: new caller → existing trace
            String trace;
            if (existingTrace == null || existingTrace.isBlank()) {
                trace = callerInfo;
            } else {
                trace = callerInfo + TRACE_SEPARATOR + existingTrace;
            }

            // Apply cross-service prefix if set
            if (tracePrefix != null) {
                trace = ">>> " + tracePrefix + " >>> " + trace;

                // Namespace detail keys with prefix
                Map<String, Object> prefixed = new HashMap<>();
                for (var entry : details.entrySet()) {
                    prefixed.put(tracePrefix + "." + entry.getKey(), entry.getValue());
                }
                details = prefixed;
            }

            return trace;
        }

        /** "com.example.service.UserService" → "UserService" */
        private static String simpleClassName(String fqcn) {
            int lastDot = fqcn.lastIndexOf('.');
            return lastDot < 0 ? fqcn : fqcn.substring(lastDot + 1);
        }
    }
}
