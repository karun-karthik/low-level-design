package org.karunkarthik.rate_limiter.core;


import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FixedWindow extends RateLimiter {
    private final Map<String, Integer> requestCount = new ConcurrentHashMap<>();
    private final Map<String, Long> windowStart = new HashMap<>();

    public FixedWindow(RateLimitConfig config) {
        super(config, RateLimitType.FIXED_WINDOW);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean allowed = new AtomicBoolean(false);
        long currentReqWindow = System.currentTimeMillis() / 1000 / config.getWindowInSeconds();
        requestCount.compute(userId, (id, count) -> {
            long lastWindow = windowStart.getOrDefault(userId, currentReqWindow);
            if (lastWindow != currentReqWindow) {
                windowStart.put(userId, currentReqWindow);
                count = 0;
            }
            if (count == null) {
                count = 0;
            }
            if (count < config.getMaxRequests()) {
                allowed.set(true);
                return count + 1;
            } else {
                allowed.set(false);
                return count;
            }
        });
        return allowed.get();
    }
}
