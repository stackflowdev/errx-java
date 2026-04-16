package io.github.stackflowdev.errx;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured application exception carrying a machine-readable code, a typed category,
 * validation fields, and debugging details.
 *
 * <p>The {@link #code()} serves two purposes: it is the programmatic error identifier
 * AND the i18n message key. Resource bundles (e.g. {@code messages_en.properties})
 * contain translations keyed by the same code.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Throw with a resolvable code (resolved via Spring MessageSource)
 * throw ErrxException.create()
 *         .code("user.not_found")
 *         .type(ErrorType.NOT_FOUND)
 *         .args(userId)
 *         .build();
 *
 * // Throw with an explicit (non-translated) message
 * throw ErrxException.create()
 *         .code("db.query_failed")
 *         .message("Could not reach primary replica")
 *         .type(ErrorType.INTERNAL)
 *         .build();
 *
 * // Wrap an existing exception, carrying metadata forward
 * throw ErrxException.wrap(sqlException)
 *         .code("db.error")
 *         .details(Map.of("query", sql))
 *         .build();
 * }</pre>
 *
 * <p>Exceptions of this type preserve Java's native {@link Throwable#getCause() cause chain}
 * and stack traces — no custom trace string is built. To inspect propagation paths,
 * use {@link #getStackTrace()} or log the exception with SLF4J
 * ({@code log.error("failed", ex)}).
 */
public class ErrxException extends RuntimeException {

    // ── Constants ────────────────────────────────────────────────────

    /** Default code when none is specified. */
    public static final String DEFAULT_CODE = "unspecified";

    /** Default error type when none is specified. */
    public static final ErrorType DEFAULT_TYPE = ErrorType.INTERNAL;

    // ── Fields ───────────────────────────────────────────────────────

    private final String code;
    private final ErrorType type;
    private final Object[] args;
    private final Map<String, String> fields;
    private final Map<String, Object> details;
    private final boolean hasExplicitMessage;

    // ── Constructor (private — use builder) ──────────────────────────

    private ErrxException(Builder builder) {
        super(builder.message, builder.cause);
        this.code = builder.code;
        this.type = builder.type;
        this.args = builder.args;
        this.fields = Collections.unmodifiableMap(builder.fields);
        this.details = Collections.unmodifiableMap(builder.details);
        this.hasExplicitMessage = builder.hasExplicitMessage;
    }

    // ── Accessors ────────────────────────────────────────────────────

    /** Machine-readable error code and i18n message key (e.g. {@code "user.not_found"}). */
    public String code() {
        return code;
    }

    /** Error category. */
    public ErrorType type() {
        return type;
    }

    /**
     * Message format arguments for i18n resolution.
     * Used as {@code {0}, {1}, ...} placeholders in resource bundle templates.
     */
    public Object[] args() {
        return args;
    }

    /** Validation field errors. Keys = field names, values = error descriptions. */
    public Map<String, String> fields() {
        return fields;
    }

    /** Additional debugging metadata. Never exposed to clients. */
    public Map<String, Object> details() {
        return details;
    }

    /**
     * True if the caller set an explicit message via {@link Builder#message(String)}.
     * When true, i18n resolvers should skip bundle lookup and use {@link #getMessage()} directly.
     */
    public boolean hasExplicitMessage() {
        return hasExplicitMessage;
    }

    // ── Formatted output ─────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("[%s: %s] - %s", type.name(), code, getMessage());
    }

    // ── Static factory methods ───────────────────────────────────────

    /** Start building a new exception. */
    public static Builder create() {
        return new Builder(null, null, false);
    }

    /**
     * Wrap an existing throwable, preserving its message and cause chain.
     *
     * <p>If the throwable is already an {@link ErrxException}, its metadata
     * (code, type, fields, details, args) is carried over and can be
     * selectively overridden via the builder.
     *
     * @return builder pre-populated with the original exception's data,
     *         or {@code null} if cause is null
     */
    public static Builder wrap(Throwable cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof ErrxException ex) {
            Builder b = new Builder(ex.getMessage(), ex.getCause(), ex.hasExplicitMessage);
            b.code = ex.code;
            b.type = ex.type;
            b.args = ex.args;
            b.fields = new HashMap<>(ex.fields);
            b.details = new HashMap<>(ex.details);
            return b;
        }
        // Plain exception — adopt its message as explicit (no bundle lookup will be attempted)
        return new Builder(cause.getMessage(), cause, cause.getMessage() != null);
    }

    // ── Static utility methods ───────────────────────────────────────

    /** Extract the error code from any throwable. Returns {@link #DEFAULT_CODE} if not an ErrxException. */
    public static String getCode(Throwable t) {
        return t instanceof ErrxException ex ? ex.code() : DEFAULT_CODE;
    }

    /** Extract the error type from any throwable. Returns {@link #DEFAULT_TYPE} if not an ErrxException. */
    public static ErrorType getType(Throwable t) {
        return t instanceof ErrxException ex ? ex.type() : DEFAULT_TYPE;
    }

    /** Check if the error's code matches any of the given codes. */
    public static boolean isCodeIn(Throwable t, String... codes) {
        return Arrays.asList(codes).contains(getCode(t));
    }

    /**
     * Convert any throwable to ErrxException.
     * If already an ErrxException, returns as-is. Otherwise wraps with defaults.
     */
    public static ErrxException asErrxException(Throwable t) {
        Objects.requireNonNull(t, "Cannot convert null to ErrxException");
        if (t instanceof ErrxException ex) {
            return ex;
        }
        return wrap(t).build();
    }

    /**
     * Wrap the exception with a different type if its code matches any of the given codes.
     * If the code doesn't match, the original type is preserved.
     */
    public static ErrxException wrapWithTypeOnCodes(Throwable cause, ErrorType type, String... codes) {
        if (cause == null) {
            return null;
        }
        Builder b = wrap(cause);
        if (Arrays.asList(codes).contains(b.code)) {
            b.type = type;
        }
        return b.build();
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static class Builder {

        private String message;
        private final Throwable cause;

        private String code = DEFAULT_CODE;
        private ErrorType type = DEFAULT_TYPE;
        private Object[] args = new Object[0];
        private Map<String, String> fields = new HashMap<>();
        private Map<String, Object> details = new HashMap<>();
        private boolean hasExplicitMessage;

        private Builder(String message, Throwable cause, boolean hasExplicitMessage) {
            this.message = message;
            this.cause = cause;
            this.hasExplicitMessage = hasExplicitMessage;
        }

        /** Set the machine-readable code (also used as i18n message key). */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /** Set the error type. */
        public Builder type(ErrorType type) {
            this.type = type;
            return this;
        }

        /**
         * Set an explicit (non-translated) message. Overrides any i18n bundle lookup
         * performed by downstream handlers.
         */
        public Builder message(String message) {
            this.message = message;
            this.hasExplicitMessage = message != null;
            return this;
        }

        /** Set message format arguments used by i18n resolvers for {@code {0}, {1}, ...} placeholders. */
        public Builder args(Object... args) {
            this.args = args == null ? new Object[0] : args;
            return this;
        }

        /** Set validation field errors. Replaces any existing fields. */
        public Builder fields(Map<String, String> fields) {
            if (fields == null) {
                return this;
            }
            this.fields = new HashMap<>(fields);
            return this;
        }

        /**
         * Add debugging details. Merges with existing details. If a key already exists
         * and both values are strings, they are concatenated with {@code " | "} separator.
         */
        public Builder details(Map<String, Object> details) {
            if (details == null) {
                return this;
            }
            for (var entry : details.entrySet()) {
                String key = entry.getKey();
                Object newVal = entry.getValue();
                Object existingVal = this.details.get(key);

                if (existingVal instanceof String es && newVal instanceof String ns) {
                    this.details.put(key, ns + " | " + es);
                } else {
                    this.details.put(key, newVal);
                }
            }
            return this;
        }

        /** Build the exception. */
        public ErrxException build() {
            return new ErrxException(this);
        }
    }
}
