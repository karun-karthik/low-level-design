package org.karunkarthik.rate_limiter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable limit contract shared by all {@link org.karunkarthik.rate_limiter.core.RateLimiter} implementations.
 *
 * <p>Interpretation depends on algorithm:</p>
 * <ul>
 *   <li><b>Token bucket</b> — {@code maxRequests} = bucket capacity; full refill to capacity every {@code windowInSeconds}</li>
 *   <li><b>Fixed / sliding window</b> — at most {@code maxRequests} within any {@code windowInSeconds} window</li>
 * </ul>
 *
 * <p>Example: {@code new RateLimitConfig(100, 60)} → 100 requests per 60-second window (or bucket).</p>
 */
@Getter
@AllArgsConstructor
public class RateLimitConfig {

    /** Maximum requests allowed within one window (or bucket capacity). */
    private final int maxRequests;

    /** Window length in seconds (fixed window width or sliding window span). */
    private final int windowInSeconds;
}
