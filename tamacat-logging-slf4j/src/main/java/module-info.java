/**
 * SLF4J 2.x service provider backed by tamacat-logging.
 */
module org.tamacat.logging.slf4j {
    requires transitive org.tamacat.logging;
    requires org.slf4j;

    // No exports: the provider is only reached through ServiceLoader.

    provides org.slf4j.spi.SLF4JServiceProvider with org.tamacat.logging.slf4j.TamacatServiceProvider;
}
