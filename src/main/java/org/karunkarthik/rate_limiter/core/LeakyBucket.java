package org.karunkarthik.rate_limiter.core;

import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leaky Bucket — requests enter a bucket; volume drains at a fixed rate over time.
 *
 * <pre>
 *   Capacity = maxRequests
 *   Leak rate = maxRequests / windowInSeconds  (units per second)
 *
 *        requests in ──▶ ┌─────────────────────────┐ ──▶ fixed outflow (leak)
 *                      │  ████████░░  level 8/10  │
 *                      └─────────────────────────┘
 *                                 │
 *                                 └── level drops continuously when idle
 *
 *   Token bucket vs leaky bucket (interview):
 *     Token bucket  → credits accumulate while idle (refill tokens)
 *     Leaky bucket  → backlog drains while idle (leak water); shapes bursts into steady flow
 *
 *   Steps on allowRequest:
 *     1. Leak: reduce level by elapsedTime × leakRate
 *     2. If level &lt; capacity → ALLOW and add 1 to level
 *     3. Else → REJECT (bucket full)
 *
 *   Time: O(1)   Space: O(users)
 *   (+) smooth, predictable outflow  (−) bursts fill the bucket quickly, then reject
 * </pre>
 */
public class LeakyBucket extends RateLimiter {

    /** Current fill level per user (0 = empty bucket). */
    private final ConcurrentHashMap<String, Double> waterLevelByUserId = new ConcurrentHashMap<>();

    /** Last time (epoch ms) leak was applied for this user. */
    private final ConcurrentHashMap<String, Long> lastLeakEpochMillisByUserId = new ConcurrentHashMap<>();

    public LeakyBucket(RateLimitConfig config) {
        super(config, RateLimitType.LEAKY_BUCKET);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean requestAllowed = new AtomicBoolean(false);
        long requestTimeMillis = System.currentTimeMillis();

        waterLevelByUserId.compute(userId, (id, currentLevel) -> {
            double levelAfterLeak = leak(userId, requestTimeMillis, currentLevel);

            if (levelAfterLeak < config.getMaxRequests()) {
                requestAllowed.set(true);
                return levelAfterLeak + 1;
            }

            requestAllowed.set(false);
            return levelAfterLeak;
        });

        return requestAllowed.get();
    }

    /**
     * Drains the bucket based on elapsed time. Example: 10 req / 60 s → leak 10/60 units per second.
     */
    private double leak(String userId, long requestTimeMillis, Double currentLevel) {
        double level = currentLevel == null ? 0.0 : currentLevel;
        double leakRatePerMillis = (double) config.getMaxRequests()
                / (config.getWindowInSeconds() * 1000L);

        lastLeakEpochMillisByUserId.putIfAbsent(userId, requestTimeMillis);
        long lastLeakMillis = lastLeakEpochMillisByUserId.get(userId);
        long elapsedMillis = requestTimeMillis - lastLeakMillis;

        if (elapsedMillis > 0) {
            level = Math.max(0, level - elapsedMillis * leakRatePerMillis);
            lastLeakEpochMillisByUserId.put(userId, requestTimeMillis);
        }
        return level;
    }
}
