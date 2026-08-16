# Rate Limiter — LLD Study Notes

Interview-oriented low-level design for **per-user API rate limiting**.  
All five `RateLimitType` values have concrete implementations; tier-based policy is wired through a factory and service layer.

---

## Package layout

```
rate_limiter/
├── Main.java                 # runnable demos
├── core/
│   ├── RateLimiter.java      # abstract strategy
│   ├── TokenBucket.java
│   ├── LeakyBucket.java
│   ├── FixedWindow.java
│   ├── SlidingWindowLog.java
│   └── SlidingWindowCounter.java
├── factory/
│   └── RateLimiterFactory.java
├── service/
│   └── RateLimiterService.java
└── model/
    ├── RateLimitConfig.java  # maxRequests + windowInSeconds
    ├── RateLimitType.java
    ├── User.java
    └── UserTier.java
```

**Flow:** `Main` → `RateLimiterService.allowRequest(User)` → picks limiter by tier → `RateLimiter.allowRequest(userId)`.

---

## Config

`RateLimitConfig(maxRequests, windowInSeconds)`

| Field | Meaning |
|-------|---------|
| `maxRequests` | Max allowed requests in one window (or bucket capacity) |
| `windowInSeconds` | Window length in seconds |

Example: `new RateLimitConfig(100, 60)` → 100 requests per 60 seconds.

**Current tier policy**

| Tier | Algorithm | Limit |
|------|-----------|-------|
| FREE | Token Bucket | 10 / 60 s |
| PREMIUM | Fixed Window | 100 / 60 s |

---

## Algorithms

### 1. Token Bucket (`TokenBucket.java`)

**Idea:** A bucket holds tokens. Each request consumes one. Tokens refill steadily over time. Bursts are allowed up to bucket capacity.

```
Capacity = maxRequests
Refill   = 1 token every (windowInSeconds / maxRequests) seconds

      ┌─────────────────────────┐
      │  Bucket (capacity = N)  │
      │  ████████░░  8 / 10     │  ← allowed request costs 1 token
      └─────────────────────────┘
                 ▲
                 └── refill computed lazily on each request
```

**Per request**
1. Refill tokens from elapsed time since last refill
2. If balance > 0 → allow and decrement
3. Else → reject

**State per user:** token balance, last refill timestamp

| | |
|---|---|
| Time | O(1) |
| Space | O(users) |
| Pros | Smooth traffic, natural bursts |
| Cons | Refill is approximate without fractional tokens |

**vs Leaky Bucket:** Token bucket *adds* credit while idle; leaky bucket *drains* backlog while idle.

---

### 2. Leaky Bucket (`LeakyBucket.java`)

**Idea:** Requests add water to a bucket. The bucket leaks at a fixed rate. If full, reject.

```
Capacity = maxRequests
Leak rate = maxRequests / windowInSeconds  (units per second)

     requests in ──▶ ┌─────────────────────────┐ ──▶ steady leak out
                   │  ████████░░  level 8/10  │
                   └─────────────────────────┘
```

**Per request**
1. Leak: reduce level by `elapsedTime × leakRate`
2. If level < capacity → allow and add 1
3. Else → reject

**State per user:** water level, last leak timestamp

| | |
|---|---|
| Time | O(1) |
| Space | O(users) |
| Pros | Smooth predictable outflow |
| Cons | Bursts fill bucket fast; stricter than token bucket for same params |

---

### 3. Fixed Window (`FixedWindow.java`)

**Idea:** Divide time into fixed buckets. Count requests in the current bucket; reset when the bucket changes.

```
windowIndex = epochSeconds / windowInSeconds

Window 0              Window 1              Window 2
|----- 60 s -----|----- 60 s -----|----- 60 s -----|
count: 8/10       count: 0/10       count: 0/10
```

**Per request**
1. Compute current window index
2. If stored index ≠ current → reset count to 0
3. If count < maxRequests → allow and increment
4. Else → reject

**State per user:** request count, window index

| | |
|---|---|
| Time | O(1) |
| Space | O(users) |
| Pros | Simplest, fastest, easy to store |
| Cons | **Boundary spike** at window edges |

**Boundary spike (common follow-up)**  
Limit = 100/min. Client sends 100 requests at `00:59` and 100 at `01:00` → **200 requests in ~2 seconds**.

---

### 4. Sliding Window Log (`SlidingWindowLog.java`)

**Idea:** Keep a queue of request timestamps. Drop entries outside the rolling window. Allow only if count < limit.

```
Window = last windowInSeconds, anchored at "now"

stale (evict)                    window start           now
   x    x    ·    ·    ·    *    *    *    *    *
   └──── outside window ────────┘└── in-window ──────┘
```

**Per request**
1. Poll timestamps older than `(now - windowInSeconds)`
2. If queue size < maxRequests → allow and append `now`
3. Else → reject

**State per user:** FIFO queue of timestamps

| | |
|---|---|
| Time | O(R) — R = requests still in window |
| Space | O(users × maxRequests) worst case |
| Pros | Exact sliding window, no boundary spike |
| Cons | More memory, cleanup on every request |

---

### 5. Sliding Window Counter (`SlidingWindowCounter.java`)

**Idea:** Keep counts for the current and previous fixed windows. Estimate sliding-window usage with a weighted sum — O(1) without storing every timestamp.

```
Window N-1 (prev)          Window N (current)
|-------- 60 s --------|-------- 60 s --------|
count: 40               count: 20
         └─ weight ────▶ 40 × (30s left / 60s) = 20

estimated = 20 + 20 = 40  →  compare to maxRequests
```

**Per request**
1. Compute current window index and elapsed time within it
2. If window rolled over → `prev = old current`, reset current
3. `estimated = prevCount × weight + currentCount`
4. If estimated < maxRequests → allow and increment current
5. Else → reject

**State per user:** window index, previous window count, current window count

| | |
|---|---|
| Time | O(1) |
| Space | O(users) |
| Pros | No timestamp log; reduces boundary spike vs pure fixed window |
| Cons | Approximate — not as exact as sliding window log |

---

## Comparison (interview cheat sheet)

| | Token Bucket | Leaky Bucket | Fixed Window | Sliding Window Log | Sliding Window Counter |
|---|:---:|:---:|:---:|:---:|:---:|
| Burst friendly | ✅ | ~ | ❌ | ❌ | ~ |
| Exact "last T seconds" | ~ | ~ | ❌ | ✅ | ~ |
| O(1) per request | ✅ | ✅ | ✅ | ❌ | ✅ |
| Low memory | ✅ | ✅ | ✅ | ❌ | ✅ |
| Boundary spike | No | No | **Yes** | No | Reduced |

---

## Design patterns used

| Pattern | Where |
|---------|-------|
| **Strategy** | `RateLimiter` + concrete algorithms |
| **Factory** | `RateLimiterFactory.createRateLimiter(type, config)` |
| **Façade** | `RateLimiterService` hides tier → limiter mapping |

---

## Thread safety

- Per-user maps use `ConcurrentHashMap`
- Counter updates use `Map.compute()` for atomic read-modify-write per key
- `AtomicBoolean` captures allow/deny inside the lambda (compute returns the new state, not a boolean)

---

## How to run

From the `LLD` module:

```bash
mvn compile exec:java -Dexec.mainClass="org.karunkarthik.rate_limiter.Main"
```

**Demos in `Main.java`**
- `checkConcurrency()` — 20 parallel requests (default); expect ~10 ALLOWED for FREE tier
- Sequential loops (commented) — step through allow/deny one request at a time

---

## Revision checklist

Before an interview, you should be able to explain:

1. **What state** each algorithm stores per user
2. **What happens** on one `allowRequest` call (step by step)
3. **Time / space** complexity
4. **Trade-offs** — especially fixed-window boundary spike vs sliding-window cost
5. **Why** FREE uses token bucket (bursts OK) vs PREMIUM uses fixed window (simple, high quota)
6. **How** you'd extend this — Redis for distributed limits, sliding window counter for memory efficiency at scale
