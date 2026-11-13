package org.tamacat.logging.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J 2.x provider, discovered via {@link java.util.ServiceLoader}.
 */
public final class TamacatServiceProvider implements SLF4JServiceProvider {

    /** Declares compatibility with the SLF4J 2.0.x API. */
    public static final String REQUESTED_API_VERSION = "2.0.99";

    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    public TamacatServiceProvider() {
    }

    @Override
    public void initialize() {
        loggerFactory = new TamacatLoggerFactory();
        markerFactory = new BasicMarkerFactory();
        mdcAdapter = new TamacatMDCAdapter();
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return REQUESTED_API_VERSION;
    }
}
