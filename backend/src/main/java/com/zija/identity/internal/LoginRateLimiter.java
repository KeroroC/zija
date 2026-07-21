package com.zija.identity.internal;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class LoginRateLimiter {

    private final int accountThreshold;
    private final int accountWindowMinutes;
    private final int ipThreshold;
    private final int ipWindowMinutes;
    private final int maxEntries;
    private final Clock clock;

    private final Map<String, Bucket> accountBuckets =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Bucket> ipBuckets =
            new LinkedHashMap<>(16, 0.75f, true);

    LoginRateLimiter() {
        this(5, 5, 20, 5, 1000, Clock.systemUTC());
    }

    LoginRateLimiter(int accountThreshold, int accountWindowMinutes,
                     int ipThreshold, int ipWindowMinutes, int maxEntries) {
        this(accountThreshold, accountWindowMinutes,
                ipThreshold, ipWindowMinutes, maxEntries, Clock.systemUTC());
    }

    LoginRateLimiter(int accountThreshold, int accountWindowMinutes,
                     int ipThreshold, int ipWindowMinutes, int maxEntries,
                     Clock clock) {
        this.accountThreshold = accountThreshold;
        this.accountWindowMinutes = accountWindowMinutes;
        this.ipThreshold = ipThreshold;
        this.ipWindowMinutes = ipWindowMinutes;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    synchronized void checkAllowed(String normalizedUsername, String ip) {
        var now = clock.millis();
        cleanup(now);
        if (isBlocked(accountBuckets.get(normalizedUsername), now)
                || isBlocked(ipBuckets.get(ip), now)) {
            throw new LoginRateLimitedException("rate limited");
        }
    }

    synchronized Result recordFailure(String normalizedUsername, String ip) {
        var now = clock.millis();
        cleanup(now);
        var accountBlocked = bump(accountBuckets, normalizedUsername, accountThreshold,
                accountWindowMinutes * 60_000L, now);
        var ipBlocked = bump(ipBuckets, ip, ipThreshold,
                ipWindowMinutes * 60_000L, now);
        evictIfFull(now);
        if (accountBlocked || ipBlocked) {
            throw new LoginRateLimitedException("rate limited");
        }
        return new Result(false);
    }

    synchronized void recordSuccess(String normalizedUsername) {
        cleanup(clock.millis());
        accountBuckets.remove(normalizedUsername);
    }

    synchronized int accountBucketCount() {
        cleanup(clock.millis());
        return accountBuckets.size();
    }

    private boolean bump(Map<String, Bucket> buckets, String key,
                         int threshold, long windowMillis, long now) {
        var bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        bucket.evictExpired(now, windowMillis);
        bucket.add(now);
        if (bucket.count() >= threshold) {
            bucket.blockUntil(now + windowMillis);
            return true;
        }
        return false;
    }

    private boolean isBlocked(Bucket bucket, long now) {
        return bucket != null && bucket.isBlocked(now);
    }

    private void cleanup(long now) {
        removeExpired(accountBuckets, now, accountWindowMinutes * 60_000L);
        removeExpired(ipBuckets, now, ipWindowMinutes * 60_000L);
    }

    private void removeExpired(Map<String, Bucket> buckets,
                               long now, long windowMillis) {
        buckets.values().removeIf(bucket -> {
            bucket.evictExpired(now, windowMillis);
            return bucket.isExpired(now);
        });
    }

    private void evictIfFull(long now) {
        evict(accountBuckets, now);
        evict(ipBuckets, now);
    }

    private void evict(Map<String, Bucket> buckets, long now) {
        while (buckets.size() > maxEntries) {
            Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
            boolean removed = false;
            while (it.hasNext()) {
                if (!it.next().getValue().isBlocked(now)) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                it = buckets.entrySet().iterator();
                it.next();
                it.remove();
            }
        }
    }

    private static final class Bucket {
        private final java.util.ArrayDeque<Long> timestamps = new java.util.ArrayDeque<>();
        private long blockedUntil;

        Bucket() {}

        void add(long ts) { timestamps.add(ts); }

        int count() { return timestamps.size(); }

        void blockUntil(long timestamp) { blockedUntil = timestamp; }

        boolean isBlocked(long now) { return blockedUntil > now; }

        boolean isExpired(long now) {
            return timestamps.isEmpty() && !isBlocked(now);
        }

        void evictExpired(long now, long windowMillis) {
            var cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (blockedUntil <= now) {
                blockedUntil = 0;
            }
        }
    }

    record Result(boolean isBlocked) {}
}
