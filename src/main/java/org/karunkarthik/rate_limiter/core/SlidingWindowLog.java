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
 */
public class SlidingWindowLog extends RateLimiter {

    /** FIFO queue of request timestamps (epoch seconds). Front = oldest. */
    private final ConcurrentHashMap<String, Queue<Long>> requestTimestampsByUserId = new ConcurrentHashMap<>();

    public SlidingWindowLog(RateLimitConfig config) {
        super(config, RateLimitType.SLIDING_WINDOW_LOG);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean requestAllowed = new AtomicBoolean(false);
        long requestTimeSeconds = System.currentTimeMillis() / 1000;

        requestTimestampsByUserId.compute(userId, (id, timestampLog) -> {
            Queue<Long> log = timestampLog == null ? new ArrayDeque<>() : timestampLog;

            // Step 1: drop expired entries from the front of the queue
            evictExpiredTimestamps(log, requestTimeSeconds);

            // Step 2 & 3: admit or reject based on in-window count
            if (log.size() < config.getMaxRequests()) {
                requestAllowed.set(true);
                log.offer(requestTimeSeconds);
            } else {
                requestAllowed.set(false);
            }
            return log;
        });

        return requestAllowed.get();
    }

    /** Polls timestamps that are no longer inside [now - window, now]. */
    private void evictExpiredTimestamps(Queue<Long> timestampLog, long requestTimeSeconds) {
        long windowStartSeconds = requestTimeSeconds - config.getWindowInSeconds();
        while (!timestampLog.isEmpty() && timestampLog.peek() <= windowStartSeconds) {
            timestampLog.poll();
        }
    }
}
