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
 *     4. If estimated &lt; maxRequests → ALLOW and increment current
 *     5. Else → REJECT
 *
 *   Time: O(1)   Space: O(users)
 *   (+) no timestamp log, softens fixed-window boundary spike  (−) approximate, not exact
 * </pre>
 */
public class SlidingWindowCounter extends RateLimiter {

    private record WindowCounterState(
            long windowIndex,
            int previousWindowCount,
            int currentWindowCount
    ) {
        static WindowCounterState forWindow(long windowIndex) {
            return new WindowCounterState(windowIndex, 0, 0);
        }
    }

    private final ConcurrentHashMap<String, WindowCounterState> stateByUserId = new ConcurrentHashMap<>();

    public SlidingWindowCounter(RateLimitConfig config) {
        super(config, RateLimitType.SLIDING_WINDOW_COUNTER);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean requestAllowed = new AtomicBoolean(false);
        long requestTimeSeconds = System.currentTimeMillis() / 1000;
        long currentWindowIndex = computeWindowIndex(requestTimeSeconds);
        long elapsedSecondsInWindow = requestTimeSeconds % config.getWindowInSeconds();

        stateByUserId.compute(userId, (id, state) -> {
            if (state == null) {
                state = WindowCounterState.forWindow(currentWindowIndex);
            }

            int previousCount = state.previousWindowCount();
            int currentCount = state.currentWindowCount();
            long storedWindowIndex = state.windowIndex();

            // Step 2: window rolled over — previous window count carries partial weight
            if (storedWindowIndex != currentWindowIndex) {
                previousCount = currentCount;
                currentCount = 0;
                storedWindowIndex = currentWindowIndex;
            }

            // Step 3: weight previous window by how much of it still overlaps the sliding window
            double previousWindowWeight =
                    (double) (config.getWindowInSeconds() - elapsedSecondsInWindow) / config.getWindowInSeconds();
            double estimatedRequestCount = previousCount * previousWindowWeight + currentCount;

            // Step 4 & 5
            if (estimatedRequestCount < config.getMaxRequests()) {
                requestAllowed.set(true);
                return new WindowCounterState(storedWindowIndex, previousCount, currentCount + 1);
            }

            requestAllowed.set(false);
            return new WindowCounterState(storedWindowIndex, previousCount, currentCount);
        });

        return requestAllowed.get();
    }

    private long computeWindowIndex(long epochSeconds) {
        return epochSeconds / config.getWindowInSeconds();
    }
}
