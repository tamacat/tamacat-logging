package org.tamacat.logging.access;

import java.time.Duration;
import java.time.Instant;

/**
 * One completed HTTP request. All string fields may be {@code null} (rendered as {@code -} or
 * omitted). Values are treated as untrusted and are always escaped and length-limited on output.
 *
 * @param timestamp     request start time ({@code null} = now)
 * @param remoteAddress client address
 * @param remoteUser    authenticated user name
 * @param method        HTTP method
 * @param requestUri    request target as received (path and query)
 * @param protocol      e.g. {@code HTTP/1.1}
 * @param status        response status code
 * @param bytesSent     response body bytes ({@code < 0} = unknown)
 * @param referer       Referer header
 * @param userAgent     User-Agent header
 * @param elapsed       time taken to serve the request ({@code null} = unknown)
 * @param requestId     request correlation id
 */
public record AccessLogEntry(
        Instant timestamp,
        String remoteAddress,
        String remoteUser,
        String method,
        String requestUri,
        String protocol,
        int status,
        long bytesSent,
        String referer,
        String userAgent,
        Duration elapsed,
        String requestId) {

    public AccessLogEntry {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder to avoid mixing up the many positional string parameters. */
    public static final class Builder {
        private Instant timestamp;
        private String remoteAddress;
        private String remoteUser;
        private String method;
        private String requestUri;
        private String protocol;
        private int status;
        private long bytesSent = -1;
        private String referer;
        private String userAgent;
        private Duration elapsed;
        private String requestId;

        private Builder() {
        }

        public Builder timestamp(Instant v) {
            timestamp = v;
            return this;
        }

        public Builder remoteAddress(String v) {
            remoteAddress = v;
            return this;
        }

        public Builder remoteUser(String v) {
            remoteUser = v;
            return this;
        }

        public Builder method(String v) {
            method = v;
            return this;
        }

        public Builder requestUri(String v) {
            requestUri = v;
            return this;
        }

        public Builder protocol(String v) {
            protocol = v;
            return this;
        }

        public Builder status(int v) {
            status = v;
            return this;
        }

        public Builder bytesSent(long v) {
            bytesSent = v;
            return this;
        }

        public Builder referer(String v) {
            referer = v;
            return this;
        }

        public Builder userAgent(String v) {
            userAgent = v;
            return this;
        }

        public Builder elapsed(Duration v) {
            elapsed = v;
            return this;
        }

        public Builder requestId(String v) {
            requestId = v;
            return this;
        }

        public AccessLogEntry build() {
            return new AccessLogEntry(timestamp, remoteAddress, remoteUser, method, requestUri, protocol,
                    status, bytesSent, referer, userAgent, elapsed, requestId);
        }
    }
}
