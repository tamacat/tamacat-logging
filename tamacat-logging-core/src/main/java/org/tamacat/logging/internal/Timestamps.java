package org.tamacat.logging.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed timestamp formats (not configurable). */
public final class Timestamps {

    private static final DateTimeFormatter ISO = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter CLF = DateTimeFormatter
            .ofPattern("dd/MMM/uuuu:HH:mm:ss Z", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private Timestamps() {
    }

    /** ISO-8601 UTC with milliseconds, e.g. {@code 2026-11-13T13:52:01.123Z}. */
    public static String iso(Instant t) {
        return ISO.format(t);
    }

    /** Apache Common Log Format time, e.g. {@code 13/Nov/2026:13:52:01 +0000}. */
    public static String clf(Instant t) {
        return CLF.format(t);
    }
}
