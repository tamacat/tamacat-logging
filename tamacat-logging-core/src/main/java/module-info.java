/**
 * tamacat-logging core: a dependency-free logging engine that writes to standard output only.
 *
 * <p>Provides the {@link java.lang.System.LoggerFinder} backend and the access log API.
 * Intentionally has no lookups, no expression evaluation, no network/file appenders,
 * no serialization and no plugin mechanism.
 */
module org.tamacat.logging {
    exports org.tamacat.logging;
    exports org.tamacat.logging.access;
    exports org.tamacat.logging.internal to org.tamacat.logging.slf4j;

    provides java.lang.System.LoggerFinder with org.tamacat.logging.jdk.TamacatLoggerFinder;
}
