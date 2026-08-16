package org.karunkarthik.rate_limiter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API caller identity passed into {@link org.karunkarthik.rate_limiter.service.RateLimiterService}.
 *
 * @param userId stable id used as the rate-limit key (must be consistent across requests)
 * @param tier   determines limit policy (algorithm + config)
 */
@Getter
@AllArgsConstructor
public class User {
    private final String userId;
    private final UserTier tier;
}
