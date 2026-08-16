package org.karunkarthik.rate_limiter.factory;

import org.karunkarthik.rate_limiter.core.FixedWindow;
import org.karunkarthik.rate_limiter.core.RateLimiter;
import org.karunkarthik.rate_limiter.core.SlidingWindowLog;
import org.karunkarthik.rate_limiter.core.TokenBucket;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

/**
 * Simple factory — isolates algorithm selection from {@link org.karunkarthik.rate_limiter.service.RateLimiterService}.
 *
 * <p>Interview talking point: factory + strategy pattern lets you swap algorithms per tenant/tier
 * without changing the service API.</p>
 */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
    }

    /**
     * @param algorithm which limiting strategy to instantiate
     * @param config    shared limit parameters (max requests, window length)
     * @return a fresh limiter instance; state is empty until first {@code allowRequest}
     * @throws IllegalArgumentException if {@code algorithm} is not implemented in this project
     */
    public static RateLimiter createRateLimiter(RateLimitType algorithm, RateLimitConfig config) {
        return switch (algorithm) {
            case TOKEN_BUCKET -> new TokenBucket(config);
            case FIXED_WINDOW -> new FixedWindow(config);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLog(config);
            default -> throw new IllegalArgumentException(
                    "Algorithm not implemented in this study project: " + algorithm
                            + ". Implemented: TOKEN_BUCKET, FIXED_WINDOW, SLIDING_WINDOW_LOG.");
        };
    }
}
