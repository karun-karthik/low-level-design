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
 * 
 *
 * Example:
 * 10 requests / 60 seconds
 *
 * Time is divided into fixed windows:
 *
 * Window 0 → 0-59 sec
 * Window 1 → 60-119 sec
 * Window 2 → 120-179 sec
 *
 * Each user gets a separate counter for the current window.
 *
 */
public class FixedWindow extends RateLimiter {

    // Number of requests made by each user in the current window.
    private final ConcurrentHashMap<String, Integer> requestCountByUserId =
            new ConcurrentHashMap<>();

    // Current window number for each user.
    private final ConcurrentHashMap<String, Long> windowIndexByUserId =
            new ConcurrentHashMap<>();

    public FixedWindow(RateLimitConfig config) {
        super(config, RateLimitType.FIXED_WINDOW);
    }

    @Override
    public boolean allowRequest(String userId) {

        AtomicBoolean allowed = new AtomicBoolean(false);

        // Example: epochSeconds / 60 → current 60-second window.
        long currentWindowIndex =
                computeWindowIndex(System.currentTimeMillis() / 1000);

        /*
         * compute() makes the read → update operation atomic
         * for this user.
         */
        requestCountByUserId.compute(userId, (id, requestCount) -> {

            long storedWindowIndex =
                    windowIndexByUserId.getOrDefault(
                            userId,
                            currentWindowIndex
                    );

            /*
             * New window → reset counter.
             *
             * Example:
             * old window = 5
             * current   = 6
             *
             * 7 requests → reset to 0
             */
            if (storedWindowIndex != currentWindowIndex) {
                windowIndexByUserId.put(userId, currentWindowIndex);
                requestCount = 0;
            }

            // First request from this user.
            if (requestCount == null) {
                requestCount = 0;
            }

            /*
             * Under limit → allow and increment.
             * At limit    → reject.
             */
            if (requestCount < config.getMaxRequests()) {
                allowed.set(true);
                return requestCount + 1;
            }

            allowed.set(false);
            return requestCount;
        });

        return allowed.get();
    }

    /**
     * Converts absolute time into a fixed window number.
     *
     * Example:
     *
     * 125 seconds / 60 = 2
     *
     * Therefore, 125 seconds belongs to window 2.
     */
    private long computeWindowIndex(long epochSeconds) {
        return epochSeconds / config.getWindowInSeconds();
    }
}