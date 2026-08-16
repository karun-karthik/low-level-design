package org.karunkarthik.rate_limiter.core;

import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sliding Window Counter — hybrid of fixed windows; approximates a sliding window in O(1).
 *
 * <pre>
 *   Keeps two fixed-window counters: previous + current.
 *   Weighted estimate = prevCount × (remaining time in prev window %) + currCount
 *
 *   Window N-1 (prev)          Window N (current)
 *   |-------- 60 s --------|-------- 60 s --------|
 *   count: 40               count: 20
 *            └─ weight ─────▶ 40 × (30s left / 60s) = 20
 *
 *   estimated = 20 + 20 = 40  →  compare against maxRequests
 *
 *   Steps on allowRequest:
 *     1. Compute current window index and how far we are into it
 *     2. If window rolled over → shift prev = old current, reset current
 *     3. estimated = prevCount × weight + currentCount
 *     4. If estimated < maxRequests → ALLOW and increment current
 *     5. Else → REJECT
 *
 *   Time: O(1)   Space: O(users)
 *   (+) no timestamp log, softens fixed-window boundary spike  (−) approximate, not exact
 * </pre>
 * 
 * Example:
 * 10 requests / last 60 seconds
 *
 * Stores the count of requests in the previous and current windows.
 *
 * On each request:
 * 1. Compute the current window index and how far we are into it.
 * 2. If the window rolled over → shift prev = old current, reset current.
 * 3. Estimate the requests in the last T seconds.
 * 4. If the estimated requests < maxRequests → ALLOW and increment current.
 * 5. Otherwise → REJECT.
 */
public class SlidingWindowCounter extends RateLimiter {

    /*
     * Immutable state for one user's two windows.
     *
     * windowIndex           → current window
     * previousWindowCount   → requests in previous window
     * currentWindowCount    → requests in current window
     */
    private record WindowCounterState(
            long windowIndex,
            int previousWindowCount,
            int currentWindowCount
    ) {

        static WindowCounterState forWindow(long windowIndex) {
            return new WindowCounterState(windowIndex, 0, 0);
        }
    }

    private final ConcurrentHashMap<String, WindowCounterState>
            stateByUserId = new ConcurrentHashMap<>();

    public SlidingWindowCounter(RateLimitConfig config) {
        super(config, RateLimitType.SLIDING_WINDOW_COUNTER);
    }

    @Override
    public boolean allowRequest(String userId) {

        long now = System.currentTimeMillis() / 1000;

        long currentWindowIndex =
                computeWindowIndex(now);

        // How far we are into the current window.
        long elapsedSecondsInWindow =
                now % config.getWindowInSeconds();

        AtomicBoolean allowed = new AtomicBoolean(false);

        /*
         * compute() makes the state update atomic for this user.
         */
        stateByUserId.compute(userId, (id, state) -> {

            // First request → start with an empty current window.
            if (state == null) {
                state = WindowCounterState
                        .forWindow(currentWindowIndex);
            }

            int previousCount =
                    state.previousWindowCount();

            int currentCount =
                    state.currentWindowCount();

            long storedWindowIndex =
                    state.windowIndex();

            /*
             * New window started.
             *
             * Old current → previous
             * New current → 0
             */
            if (storedWindowIndex != currentWindowIndex) {

                previousCount = currentCount;
                currentCount = 0;

                storedWindowIndex = currentWindowIndex;
            }

            /*
             * Calculate how much of the previous window
             * overlaps the current sliding window.
             *
             * Example:
             * 60 sec window, 20 sec elapsed
             *
             * weight = (60 - 20) / 60
             *        = 0.667
             */
            double previousWindowWeight =
                    (double) (
                            config.getWindowInSeconds()
                                    - elapsedSecondsInWindow
                    ) / config.getWindowInSeconds();

            /*
             * Estimate requests in the last T seconds.
             */
            double estimatedRequestCount =
                    previousCount * previousWindowWeight
                            + currentCount;

            /*
             * Under limit → allow and increment current count.
             */
            if (estimatedRequestCount < config.getMaxRequests()) {

                allowed.set(true);

                return new WindowCounterState(
                        storedWindowIndex,
                        previousCount,
                        currentCount + 1
                );
            }

            // Limit reached → reject without changing state.
            allowed.set(false);

            return new WindowCounterState(
                    storedWindowIndex,
                    previousCount,
                    currentCount
            );
        });

        return allowed.get();
    }

    /**
     * Converts absolute time into a fixed window number.
     *
     * Example:
     * 125 / 60 = 2
     */
    private long computeWindowIndex(long epochSeconds) {
        return epochSeconds / config.getWindowInSeconds();
    }
}