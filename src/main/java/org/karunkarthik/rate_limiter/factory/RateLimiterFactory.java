package org.karunkarthik.rate_limiter.factory;

import org.karunkarthik.rate_limiter.core.FixedWindow;
import org.karunkarthik.rate_limiter.core.RateLimiter;
import org.karunkarthik.rate_limiter.core.SlidingWindowLog;
import org.karunkarthik.rate_limiter.core.TokenBucket;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

public class RateLimiterFactory {
    public static RateLimiter createRateLimiter(RateLimitType algo, RateLimitConfig config) {
        return switch (algo) {
            case TOKEN_BUCKET -> new TokenBucket(config);
            case FIXED_WINDOW -> new FixedWindow(config);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLog(config);
            default -> throw new IllegalArgumentException("Unsupported rate limiting algorithm: " + algo);
        };
    }
}
