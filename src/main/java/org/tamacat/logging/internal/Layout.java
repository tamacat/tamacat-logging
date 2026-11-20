package org.tamacat.logging.internal;

/**
 * Converts an event into one output record. Sealed: no other implementations can be plugged in.
 * Every implementation must end the record with exactly one {@code '\n'} and must escape all
 * event data.
 */
public sealed interface Layout permits TextLayout, EcsJsonLayout {

    void format(LogEvent event, StringBuilder out);
}
