package org.karunkarthik.rate_limiter.core;


import org.karunkarthik.rate_limiter.model.RateLimitConfig;
import org.karunkarthik.rate_limiter.model.RateLimitType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlidingWindowLog extends RateLimiter {
    private final Map<String, Queue<Long>> requestLog = new ConcurrentHashMap<>();


    public SlidingWindowLog(RateLimitConfig config) {
        super(config, RateLimitType.SLIDING_WINDOW_LOG);
    }

    @Override
    public boolean allowRequest(String userId) {
        AtomicBoolean allowed = new AtomicBoolean(false);
        long now = System.currentTimeMillis() / 1000;
        requestLog.compute(userId, (id, log) -> {
            if (log == null)    log = new ArrayDeque<>();
            while (!log.isEmpty() && (now - log.peek() >= config.getWindowInSeconds())) {
                log.poll();
            }

            if (log.size() < config.getMaxRequests()) {
                allowed.set(true);
                log.offer(now);
            } else {
                allowed.set(false);
            }
            return log;
        });
        return allowed.get();
    }
}
