package org.karunkarthik.rate_limiter.core;

import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Token Bucket — burst-friendly limiter with steady token refill.
 *
 * <pre>
 *   Capacity = maxRequests tokens
 *   Refill   = 1 token every (windowInSeconds / maxRequests) seconds
 *
 *        ┌─────────────────────────┐
 *        │  Bucket (capacity = N)  │
 *        │  ████████░░  8 / 10     │  ← each allowed request costs 1 token
 *        └─────────────────────────┘
 *                   ▲
 *                   └── tokens drip in over time (computed lazily on each request)
 *
 *   Steps on allowRequest:
 *     1. Refill tokens based on elapsed time
 *     2. If balance > 0 → ALLOW and decrement
 *     3. Else → REJECT
 *
 *   Time: O(1)   Space: O(users)
 *   (+) smooth shaping, allows bursts  (−) approximate refill without fractional tokens
 * </pre>
 * 
 * Token Bucket:
 * - Bucket starts full.
 * - Each request consumes 1 token.
 * - Tokens are refilled based on elapsed time.
 * - Empty bucket → request rejected.
 */
public class TokenBucket extends RateLimiter {

    // Current tokens available for each user.
    private final Map<String, Integer> tokenBalanceByUserId =
            new ConcurrentHashMap<>();

    // Last time we processed a refill for each user.
    private final Map<String, Long> lastRefillEpochMillisByUserId =
            new ConcurrentHashMap<>();

    public TokenBucket(RateLimitConfig config) {
        super(config, RateLimitType.TOKEN_BUCKET);
    }

    @Override
    public boolean allowRequest(String userId) {

        long requestTimeMillis = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        /*
         * compute() makes the read → update operation atomic
         * for this user, so concurrent requests don't both
         * consume the same token.
         */
        tokenBalanceByUserId.compute(userId, (id, currentBalance) -> {

            int tokens = refillTokens(userId, requestTimeMillis);

            if (tokens > 0) {
                allowed.set(true);
                return tokens - 1; // Consume one token.
            }

            return tokens; // 0 → reject.
        });

        return allowed.get();
    }

    private int refillTokens(String userId, long requestTimeMillis) {

        // Example: 10 requests / 60 sec → 1 token every 6 sec.
        double secondsPerToken =
                (double) config.getWindowInSeconds()
                        / config.getMaxRequests();

        /*
         * First request: initialize the clock.
         *
         * putIfAbsent() is important:
         * don't reset the timestamp on every request.
         */
        lastRefillEpochMillisByUserId.putIfAbsent(
                userId, requestTimeMillis);

        long lastRefillMillis =
                lastRefillEpochMillisByUserId.get(userId);

        long elapsedSeconds =
                (requestTimeMillis - lastRefillMillis) / 1000;

        // How many whole tokens did we earn?
        int tokensToAdd =
                (int) (elapsedSeconds / secondsPerToken);

        // New user → bucket starts full.
        int currentBalance =
                tokenBalanceByUserId.getOrDefault(
                        userId,
                        config.getMaxRequests());

        // Never allow bucket to exceed its capacity.
        int newBalance = Math.min(
                config.getMaxRequests(),
                currentBalance + tokensToAdd
        );

        /*
         * Only move the timestamp when we actually added tokens.
         * Otherwise, elapsed partial time would be lost.
         */
        if (tokensToAdd > 0) {
            lastRefillEpochMillisByUserId.put(
                    userId, requestTimeMillis);
        }

        return newBalance;
    }
}