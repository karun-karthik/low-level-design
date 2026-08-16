package org.karunkarthik.rate_limiter.model;

/**
 * Supported rate-limiting algorithms for factory wiring and tier configuration.
 *
 * <p>Implemented in this project: {@link #TOKEN_BUCKET}, {@link #FIXED_WINDOW}, {@link #SLIDING_WINDOW_LOG}.
 * Others are listed for interview comparison / future extension.</p>
 */
public enum RateLimitType {

    /** Burst-tolerant bucket with steady token refill. See {@code TokenBucket}. */
    TOKEN_BUCKET,

    /** Requests drip out at a fixed rate (not implemented here — contrast with token bucket in interviews). */
    LEAKY_BUCKET,

    /** Counter resets on fixed clock-aligned windows; watch for boundary spikes. */
    FIXED_WINDOW,

    /** Exact sliding window via timestamp queue; higher memory, highest precision. */
    SLIDING_WINDOW_LOG,

    /** Hybrid: fixed window counters + weighted previous window (not implemented here). */
    SLIDING_WINDOW_COUNTER
}
