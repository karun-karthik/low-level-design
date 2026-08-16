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
 */
public class TokenBucket extends RateLimiter {

    /** Tokens remaining per user. No entry means bucket is full. */
    private final Map<String, Integer> tokenBalanceByUserId = new ConcurrentHashMap<>();

    /** Last time (epoch ms) we applied a refill for this user. */
    private final Map<String, Long> lastRefillEpochMillisByUserId = new ConcurrentHashMap<>();

    public TokenBucket(RateLimitConfig config) {
        super(config, RateLimitType.TOKEN_BUCKET);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean requestAllowed = new AtomicBoolean(false);
        long requestTimeMillis = System.currentTimeMillis();

        // compute() gives atomic read-modify-write per userId (thread-safe counter update)
        tokenBalanceByUserId.compute(userId, (id, currentBalance) -> {
            int tokensAfterRefill = refillTokens(userId, requestTimeMillis);

            if (tokensAfterRefill > 0) {
                requestAllowed.set(true);
                return tokensAfterRefill - 1; // consume one token
            }

            requestAllowed.set(false);
            return tokensAfterRefill; // bucket empty — reject without changing balance
        });

        return requestAllowed.get();
    }

    /**
     * Adds tokens for time elapsed since last refill.
     * Example: 10 req / 60 s → 1 token every 6 s.
     */
    private int refillTokens(String userId, long requestTimeMillis) {
        double secondsPerToken = (double) config.getWindowInSeconds() / config.getMaxRequests();

        lastRefillEpochMillisByUserId.putIfAbsent(userId, requestTimeMillis);
        long lastRefillMillis = lastRefillEpochMillisByUserId.get(userId);
        long elapsedSeconds = (requestTimeMillis - lastRefillMillis) / 1000;

        int tokensToAdd = (int) (elapsedSeconds / secondsPerToken);
        int currentBalance = tokenBalanceByUserId.getOrDefault(userId, config.getMaxRequests());
        int refilledBalance = Math.min(config.getMaxRequests(), currentBalance + tokensToAdd);

        if (tokensToAdd > 0) {
            lastRefillEpochMillisByUserId.put(userId, requestTimeMillis);
        }
        return refilledBalance;
    }
}
