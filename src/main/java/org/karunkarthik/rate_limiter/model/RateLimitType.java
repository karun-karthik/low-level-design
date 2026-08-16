package org.karunkarthik.rate_limiter.model;

/**
 * Supported rate-limiting algorithms for factory wiring and tier configuration.
 *
 * <p>Each value maps to a concrete class in {@code core} via {@code RateLimiterFactory}.</p>
 */
public enum RateLimitType {

    /** Burst-tolerant bucket with steady token refill. See {@code TokenBucket}. */
    TOKEN_BUCKET,

    /** Bucket drains at a fixed rate; shapes bursts into steady outflow. See {@code LeakyBucket}. */
    LEAKY_BUCKET,

    /** Counter resets on fixed clock-aligned windows; watch for boundary spikes. */
    FIXED_WINDOW,

    /** Exact sliding window via timestamp queue; higher memory, highest precision. */
    SLIDING_WINDOW_LOG,

    /** Hybrid: weighted previous + current fixed-window counters. See {@code SlidingWindowCounter}. */
    SLIDING_WINDOW_COUNTER
}
