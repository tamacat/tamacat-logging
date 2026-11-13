package org.tamacat.logging.internal;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reports internal problems of the logging system to stderr, rate limited
 * (at most one message per second; the rest are counted). Never throws.
 */
public final class StatusReporter {

    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static final OutputStream ERR = new FileOutputStream(FileDescriptor.err);
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final AtomicLong LAST = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong SUPPRESSED = new AtomicLong();
    private static final AtomicLong TOTAL = new AtomicLong();

    private StatusReporter() {
    }

    public static void report(String message, Throwable cause) {
        TOTAL.incrementAndGet();
        try {
            long now = System.nanoTime();
            long last = LAST.get();
            if ((last != Long.MIN_VALUE && now - last < INTERVAL) || !LAST.compareAndSet(last, now)) {
                SUPPRESSED.incrementAndGet();
                return;
            }
            StringBuilder sb = new StringBuilder(128).append("[tamacat-logging] ");
            TextEscaper.escape(message, sb);
            if (cause != null) {
                sb.append(": ").append(cause.getClass().getName());
                String m = Safe.message(cause);
                if (m != null) {
                    sb.append(": ");
                    TextEscaper.escape(Safe.truncate(m, 512), sb);
                }
            }
            long suppressed = SUPPRESSED.getAndSet(0);
            if (suppressed > 0) {
                sb.append(" (").append(suppressed).append(" earlier messages suppressed)");
            }
            sb.append('\n');
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            LOCK.lock();
            try {
                ERR.write(bytes);
                ERR.flush();
            } finally {
                LOCK.unlock();
            }
        } catch (IOException | RuntimeException e) {
            // nothing more we can do
        }
    }

    /** Total number of reports since startup (for tests and diagnostics). */
    public static long totalReports() {
        return TOTAL.get();
    }
}
