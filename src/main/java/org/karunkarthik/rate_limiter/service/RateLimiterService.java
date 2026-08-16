package org.karunkarthik.rate_limiter.service;

import org.karunkarthik.rate_limiter.core.RateLimiter;
import org.karunkarthik.rate_limiter.factory.RateLimiterFactory;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;
import org.karunkarthik.rate_limiter.model.User;
import org.karunkarthik.rate_limiter.model.UserTier;

import java.util.HashMap;
import java.util.Map;

public class RateLimiterService {
    private final Map<UserTier, RateLimiter> rateLimiters = new HashMap<>();

    public RateLimiterService() {
        rateLimiters.put(UserTier.FREE, RateLimiterFactory.createRateLimiter(RateLimitType.TOKEN_BUCKET,
                new RateLimitConfig(10, 60)));
        rateLimiters.put(UserTier.PREMIUM, RateLimiterFactory.createRateLimiter(RateLimitType.FIXED_WINDOW,
                new RateLimitConfig(100, 60)));
    }

    public boolean allowRequest(User user) {
        RateLimiter rateLimiter = rateLimiters.get(user.getTier());
        if (rateLimiter == null) {
            throw new IllegalArgumentException("No rate limiter configured for user tier: " + user.getTier());
        }
        return rateLimiter.allowRequest(user.getUserId());
    }
}
