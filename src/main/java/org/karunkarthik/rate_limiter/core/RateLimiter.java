package org.karunkarthik.rate_limiter.core;

import lombok.AllArgsConstructor;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

/**
 * Strategy interface — each implementation decides whether one request should be allowed.
 *
 * @see TokenBucket
 * @see FixedWindow
 * @see SlidingWindowLog
 */
@AllArgsConstructor
public abstract class RateLimiter {

    protected final RateLimitConfig config;
    protected final RateLimitType type;

    /**
     * @param userId caller id used as the rate-limit key
     * @return true if allowed, false if rate limit exceeded (HTTP 429)
     */
    public abstract boolean allowRequest(String userId);
}
