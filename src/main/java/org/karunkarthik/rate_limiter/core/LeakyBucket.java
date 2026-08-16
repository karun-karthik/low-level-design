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
 *     2. If level < capacity → ALLOW and add 1 to level
 *     3. Else → REJECT (bucket full)
 *
 *   Time: O(1)   Space: O(users)
 *   (+) smooth, predictable outflow  (−) bursts fill the bucket quickly, then reject
 * </pre>
 * 
 *
 * Requests add 1 unit to the bucket.
 * The bucket continuously leaks at a fixed rate.
 *
 * Example:
 * 10 requests / 60 seconds
 *
 * Capacity   = 10
 * Leak rate  = 10 / 60 units per second
 *
 * Full bucket → reject new requests.
 *
 */
public class LeakyBucket extends RateLimiter {

    // Current bucket level for each user.
    private final ConcurrentHashMap<String, Double>
            waterLevelByUserId = new ConcurrentHashMap<>();

    // Last time we calculated the leak for each user.
    private final ConcurrentHashMap<String, Long>
            lastLeakEpochMillisByUserId = new ConcurrentHashMap<>();

    public LeakyBucket(RateLimitConfig config) {
        super(config, RateLimitType.LEAKY_BUCKET);
    }

    @Override
    public boolean allowRequest(String userId) {

        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        /*
         * Atomically update this user's bucket.
         */
        waterLevelByUserId.compute(userId, (id, currentLevel) -> {

            // First calculate how much water leaked.
            double level = leak(userId, now, currentLevel);

            /*
             * Space available → accept request
             * and add 1 unit of water.
             */
            if (level < config.getMaxRequests()) {

                allowed.set(true);
                return level + 1;
            }

            // Bucket full → reject.
            allowed.set(false);
            return level;
        });

        return allowed.get();
    }

    /**
     * Removes water based on how much time has passed.
     */
    private double leak(
            String userId,
            long now,
            Double currentLevel) {

        // New user → empty bucket.
        double level =
                currentLevel == null ? 0.0 : currentLevel;

        /*
         * Example:
         * 10 requests / 60 sec
         *
         * leakRate = 10 / 60000 units per millisecond.
         */
        double leakRatePerMillis =
                (double) config.getMaxRequests()
                        / (config.getWindowInSeconds() * 1000L);

        /*
         * First request → start the clock.
         * putIfAbsent() prevents resetting it on every request.
         */
        lastLeakEpochMillisByUserId.putIfAbsent(userId, now);

        long lastLeak =
                lastLeakEpochMillisByUserId.get(userId);

        long elapsedMillis = now - lastLeak;

        if (elapsedMillis > 0) {

            /*
             * Remove the water that should have leaked
             * during the elapsed time.
             */
            level = Math.max(
                    0,
                    level - elapsedMillis * leakRatePerMillis
            );

            // We've accounted for the leak up to 'now'.
            lastLeakEpochMillisByUserId.put(userId, now);
        }

        return level;
    }
}