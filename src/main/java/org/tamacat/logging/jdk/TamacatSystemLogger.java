package org.tamacat.logging.jdk;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.tamacat.logging.LoggerContext;
import org.tamacat.logging.internal.MessageFormatter;

/**
 * {@link System.Logger} implementation. Parameters use {@code {0}}, {@code {1}}... placeholders
 * replaced by simple index substitution (no {@link java.text.MessageFormat} sub-formats).
 */
final class TamacatSystemLogger implements System.Logger {

    private final String name;

    TamacatSystemLogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(System.Logger.Level level) {
        org.tamacat.logging.Level l = map(level);
        LoggerContext ctx = LoggerContext.get();
        return l != null && ctx != null && ctx.isEnabled(name, l);
    }

    @Override
    public void log(System.Logger.Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        org.tamacat.logging.Level l = map(level);
        LoggerContext ctx = LoggerContext.get();
        if (l == null || ctx == null || !ctx.isEnabled(name, l)) {
            return;
        }
        ctx.log(name, l, localize(bundle, msg), null, thrown);
    }

    @Override
    public void log(System.Logger.Level level, ResourceBundle bundle, String format, Object... params) {
        org.tamacat.logging.Level l = map(level);
        LoggerContext ctx = LoggerContext.get();
        if (l == null || ctx == null || !ctx.isEnabled(name, l)) {
            return;
        }
        String message = MessageFormatter.formatIndexed(localize(bundle, format), params,
                ctx.configuration().messageMaxLength());
        ctx.log(name, l, message, null, null);
    }

    private static String localize(ResourceBundle bundle, String key) {
        if (bundle == null || key == null) {
            return key;
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException | ClassCastException e) {
            return key;
        }
    }

    static org.tamacat.logging.Level map(System.Logger.Level level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case ALL, TRACE -> org.tamacat.logging.Level.TRACE;
            case DEBUG -> org.tamacat.logging.Level.DEBUG;
            case INFO -> org.tamacat.logging.Level.INFO;
            case WARNING -> org.tamacat.logging.Level.WARN;
            case ERROR -> org.tamacat.logging.Level.ERROR;
            case OFF -> null;
        };
    }
}
