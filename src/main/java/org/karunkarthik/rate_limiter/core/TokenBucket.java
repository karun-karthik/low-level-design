package org.karunkarthik.rate_limiter.core;


import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TokenBucket extends RateLimiter {
    private final Map<String, Integer> tokens = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRefillTime = new ConcurrentHashMap<>();

    public TokenBucket(RateLimitConfig config) {
        super(config, RateLimitType.TOKEN_BUCKET);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean allowed = new AtomicBoolean(false);
        long now =  System.currentTimeMillis();
        tokens.compute(userId, (id, availableTokens) -> {
            int currentTokens = refillTokens(userId, now);
            if (currentTokens > 0) {
                allowed.set(true);
                return currentTokens - 1;
            } else {
                allowed.set(false);
                return currentTokens;
            }
        });

        return allowed.get();
    }

    private int refillTokens(String userId, long now) {
        double refillRate = (double) config.getWindowInSeconds() / config.getMaxRequests();
        lastRefillTime.putIfAbsent(userId, now);
        long lastRefill = lastRefillTime.get(userId);
        long elapsed = (now - lastRefill) / 1000;

        int refillTokens = (int) (elapsed / refillRate);
        int currentTokens = tokens.getOrDefault(userId, config.getMaxRequests());
        currentTokens = Math.min(config.getMaxRequests(), currentTokens + refillTokens);

        if (refillTokens > 0) {
            lastRefillTime.put(userId, now);
        }
        return currentTokens;
    }
}
