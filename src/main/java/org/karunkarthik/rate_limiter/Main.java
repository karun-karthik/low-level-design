package org.karunkarthik.rate_limiter;

import org.karunkarthik.rate_limiter.model.User;
import org.karunkarthik.rate_limiter.model.UserTier;
import org.karunkarthik.rate_limiter.service.RateLimiterService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    /** Simulates 20 concurrent requests hitting the same user (tests thread safety). */
    static void checkConcurrency(RateLimiterService rateLimiterService) throws InterruptedException {
        User freeUser = new User("user1", UserTier.FREE);
        int threadCount = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier startGate = new CyclicBarrier(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 1; i <= threadCount; i++) {
            final int requestNumber = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start together
                } catch (Exception e) {
                    e.printStackTrace();
                }

                boolean allowed = rateLimiterService.allowRequest(freeUser);
                System.out.println(Thread.currentThread().getName()
                        + " | Request " + requestNumber + ": " + (allowed ? "ALLOWED" : "BLOCKED"));

                done.countDown();
            });
        }

        done.await();
        executor.shutdown();
    }

    public static void main(String[] args) throws InterruptedException {
        RateLimiterService rateLimiterService = new RateLimiterService();

        User freeUser = new User("user1", UserTier.FREE);       // 10 req / 60 s (token bucket)
        User premiumUser = new User("user2", UserTier.PREMIUM); // 100 req / 60 s (fixed window)

        // --- Sequential demo (uncomment to run) ---
//        System.out.println("=== Free User ===");
//        for (int i = 1; i <= 15; i++) {
//            boolean allowed = rateLimiterService.allowRequest(freeUser);
//            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
//            Thread.sleep(100);
//        }
//
//        System.out.println("\n=== Premium User ===");
//        for (int i = 1; i <= 120; i++) {
//            boolean allowed = rateLimiterService.allowRequest(premiumUser);
//            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
//            Thread.sleep(100);
//        }

        checkConcurrency(rateLimiterService);
    }
}
