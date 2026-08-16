package org.karunkarthik.rate_limiter.core;

import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fixed Window — simple counter that resets on fixed, non-overlapping time buckets.
 *
 * <pre>
 *   windowIndex = epochSeconds / windowInSeconds
 *
 *   Window 0              Window 1              Window 2
 *   |----- 60 s -----|----- 60 s -----|----- 60 s -----|
 *   count: 8/10       count: 0/10       count: 0/10
 *          ▲                                    ▲
 *     same window → increment              new window → reset counter to 0
 *
 *   Steps on allowRequest:
 *     1. Compute current window index
 *     2. If stored index ≠ current → reset count to 0
 *     3. If count < maxRequests → ALLOW and increment
 *     4. Else → REJECT
 *
 *   Time: O(1)   Space: O(users)
 *   (+) simplest and fastest  (−) boundary spike: up to 2× limit at window edges
 *
 *   Boundary spike example (100 req/min):
 *     100 requests at 00:59 (window A) + 100 at 01:00 (window B) = 200 in ~2 seconds
 * </pre>
 */
public class FixedWindow extends RateLimiter {

    /** Requests used in the user's current window. */
    private final ConcurrentHashMap<String, Integer> requestCountByUserId = new ConcurrentHashMap<>();

    /** Which fixed window bucket the counter belongs to. */
    private final ConcurrentHashMap<String, Long> windowIndexByUserId = new ConcurrentHashMap<>();

    public FixedWindow(RateLimitConfig config) {
        super(config, RateLimitType.FIXED_WINDOW);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean requestAllowed = new AtomicBoolean(false);
        long currentWindowIndex = computeWindowIndex(System.currentTimeMillis() / 1000);

        requestCountByUserId.compute(userId, (id, requestCount) -> {
            long storedWindowIndex = windowIndexByUserId.getOrDefault(userId, currentWindowIndex);

            // Step 2: new time bucket → start fresh
            if (storedWindowIndex != currentWindowIndex) {
                windowIndexByUserId.put(userId, currentWindowIndex);
                requestCount = 0;
            }
            if (requestCount == null) {
                requestCount = 0;
            }

            // Step 3 & 4: under limit → allow; at limit → reject
            if (requestCount < config.getMaxRequests()) {
                requestAllowed.set(true);
                return requestCount + 1;
            }
            requestAllowed.set(false);
            return requestCount;
        });

        return requestAllowed.get();
    }

    private long computeWindowIndex(long epochSeconds) {
        return epochSeconds / config.getWindowInSeconds();
    }
}
