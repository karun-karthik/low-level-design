package org.karunkarthik.rate_limiter.service;

import org.karunkarthik.rate_limiter.core.RateLimiter;
import org.karunkarthik.rate_limiter.factory.RateLimiterFactory;
import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;
import org.karunkarthik.rate_limiter.model.User;
import org.karunkarthik.rate_limiter.model.UserTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps each {@link UserTier} to a {@link RateLimiter} and delegates allow/deny checks.
 *
 * <p>FREE → Token Bucket (10 / 60 s) &nbsp;|&nbsp; PREMIUM → Fixed Window (100 / 60 s)</p>
 */
public class RateLimiterService {

    private final Map<UserTier, RateLimiter> rateLimiterByTier = new EnumMap<>(UserTier.class);

    public RateLimiterService() {
        rateLimiterByTier.put(
                UserTier.FREE,
                RateLimiterFactory.createRateLimiter(
                        RateLimitType.TOKEN_BUCKET,
                        new RateLimitConfig(10, 60)));
        rateLimiterByTier.put(
                UserTier.PREMIUM,
                RateLimiterFactory.createRateLimiter(
                        RateLimitType.FIXED_WINDOW,
                        new RateLimitConfig(100, 60)));
    }

    public boolean allowRequest(User user) {
        RateLimiter rateLimiter = rateLimiterByTier.get(user.getTier());
        if (rateLimiter == null) {
            throw new IllegalArgumentException("No rate limiter configured for tier: " + user.getTier());
        }
        return rateLimiter.allowRequest(user.getUserId());
    }
}
