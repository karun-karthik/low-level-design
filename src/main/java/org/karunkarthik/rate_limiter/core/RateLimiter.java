package org.karunkarthik.rate_limiter.core;

import lombok.AllArgsConstructor;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

@AllArgsConstructor
public abstract class RateLimiter {
    protected final RateLimitConfig config;
    protected final RateLimitType type;

    public abstract boolean allowRequest(String userId);
}
