package org.tamacat.logging.internal;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The only output: synchronous writes to the process's standard output.
 *
 * <p>Writes to {@link FileDescriptor#out} directly rather than {@link System#out}, so redirecting
 * {@code System.out} into a logger can never cause infinite recursion. Each record is encoded
 * first and then written with a single {@code write} + {@code flush} under a lock, so records
 * from concurrent threads never interleave. Uses {@link ReentrantLock} (not {@code synchronized})
 * to stay friendly to virtual threads.
 */
public final class StdoutSink {

    private final ReentrantLock lock = new ReentrantLock();
    private volatile OutputStream out = new FileOutputStream(FileDescriptor.out);

    /** Replaces the destination (for tests or embedding). {@code null} restores stdout. */
    public void setOutput(OutputStream os) {
        lock.lock();
        try {
            out = os != null ? os : new FileOutputStream(FileDescriptor.out);
        } finally {
            lock.unlock();
        }
    }

    public void write(CharSequence record) {
        byte[] bytes = record.toString().getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            StatusReporter.report("failed to write log record", e);
        } finally {
            lock.unlock();
        }
    }
}
