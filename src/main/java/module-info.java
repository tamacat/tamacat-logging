/**
 * tamacat-logging: a dependency-free logging implementation that writes to standard output only.
 *
 * <p>Backends for {@link java.lang.System.Logger} and, when slf4j-api is present, SLF4J 2.x.
 * slf4j-api is an optional ({@code requires static}) dependency: without it the SLF4J provider
 * is simply never loaded.
 *
 * <p>Intentionally has no lookups, no expression evaluation, no network/file appenders,
 * no serialization and no plugin mechanism.
 */
module org.tamacat.logging {
    requires static org.slf4j;

    exports org.tamacat.logging;
    exports org.tamacat.logging.access;

    provides java.lang.System.LoggerFinder with org.tamacat.logging.jdk.TamacatLoggerFinder;
    provides org.slf4j.spi.SLF4JServiceProvider with org.tamacat.logging.slf4j.TamacatServiceProvider;
}
