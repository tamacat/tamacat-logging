package org.tamacat.logging.jdk;

import java.util.Objects;

/**
 * {@link System.LoggerFinder} backend: routes {@link System#getLogger(String)} (including JDK
 * internal loggers) to tamacat-logging.
 */
public final class TamacatLoggerFinder extends System.LoggerFinder {

    public TamacatLoggerFinder() {
    }

    @Override
    public System.Logger getLogger(String name, Module module) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(module, "module");
        return new TamacatSystemLogger(name);
    }
}
