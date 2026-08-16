package org.karunkarthik.rate_limiter.core;

import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sliding Window Log — exact "N requests in the last T seconds" using a timestamp queue.
 *
 * <pre>
 *   Window = last windowInSeconds, always anchored at "now"
 *
 *   stale (evict)                         window start              now
 *      x    x    ·    ·    ·    *    *    *    *    *
 *      └──── outside window ────────────┘└── in-window requests ──┘
 *
 *   Steps on allowRequest:
 *     1. Remove timestamps older than (now - windowInSeconds)
 *     2. If log.size() < maxRequests → ALLOW and append now
 *     3. Else → REJECT
 *
 *   Time: O(R) per request (R = requests in window)   Space: O(users × maxRequests)
 *   (+) most accurate sliding window  (−) higher memory and per-request cleanup cost
 * </pre>
 * 
 * Example:
 * 10 requests / last 60 seconds
 *
 * Stores the timestamp of every request.
 *
 * On each request:
 * 1. Remove timestamps outside the sliding window.
 * 2. If remaining count < limit → allow and add timestamp.
 * 3. Otherwise → reject.
 *
 */
public class SlidingWindowLog extends RateLimiter {

    // Each user has a queue of their request timestamps.
    // Oldest timestamp is at the front.
    private final ConcurrentHashMap<String, Queue<Long>>
            requestTimestampsByUserId = new ConcurrentHashMap<>();

    public SlidingWindowLog(RateLimitConfig config) {
        super(config, RateLimitType.SLIDING_WINDOW_LOG);
    }

    @Override
    public boolean allowRequest(String userId) {

        long now = System.currentTimeMillis() / 1000;
        AtomicBoolean allowed = new AtomicBoolean(false);

        /*
         * compute() makes the update for this user atomic.
         */
        requestTimestampsByUserId.compute(userId, (id, timestampLog) -> {

            // First request → create an empty queue.
            Queue<Long> log =
                    timestampLog == null
                            ? new ArrayDeque<>()
                            : timestampLog;

            // Remove requests that are too old.
            evictExpiredTimestamps(log, now);

            /*
             * Remaining timestamps = requests in the
             * current sliding window.
             */
            if (log.size() < config.getMaxRequests()) {

                allowed.set(true);

                // Current request becomes part of the log.
                log.offer(now);

            } else {
                allowed.set(false);
            }

            return log;
        });

        return allowed.get();
    }

    /**
     * Removes timestamps outside the sliding window.
     *
     * Example:
     * now = 100
     * window = 60
     *
     * Keep timestamps > 40.
     * Remove timestamps <= 40.
     */
    private void evictExpiredTimestamps(
            Queue<Long> timestampLog,
            long now) {

        long windowStart =
                now - config.getWindowInSeconds();

        /*
         * Queue is ordered oldest → newest.
         *
         * So keep removing from the front until
         * the oldest timestamp is inside the window.
         */
        while (!timestampLog.isEmpty()
                && timestampLog.peek() <= windowStart) {

            timestampLog.poll();
        }
    }
}