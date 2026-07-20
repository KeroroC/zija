package com.zija.identity.internal;

import org.springframework.stereotype.Component;

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

    private final Map<String, Bucket> accountBuckets = new LinkedHashMap<>();
    private final Map<String, Bucket> ipBuckets = new LinkedHashMap<>();

    LoginRateLimiter() {
        this(5, 5, 20, 5, 1000);
    }

    LoginRateLimiter(int accountThreshold, int accountWindowMinutes,
                     int ipThreshold, int ipWindowMinutes, int maxEntries) {
        this.accountThreshold = accountThreshold;
        this.accountWindowMinutes = accountWindowMinutes;
        this.ipThreshold = ipThreshold;
        this.ipWindowMinutes = ipWindowMinutes;
        this.maxEntries = maxEntries;
    }

    synchronized Result recordFailure(String normalizedUsername, String ip) {
        var accountKey = normalizedUsername + "\0" + ip;
        var accountBlocked = bump(accountBuckets, accountKey, accountThreshold,
                accountWindowMinutes * 60_000L);
        var ipBlocked = bump(ipBuckets, ip, ipThreshold, ipWindowMinutes * 60_000L);
        evictIfFull();
        if (accountBlocked || ipBlocked) {
            throw new LoginRateLimitedException("rate limited");
        }
        return new Result(false);
    }

    synchronized void recordSuccess(String normalizedUsername) {
        var prefix = normalizedUsername + "\0";
        accountBuckets.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    private boolean bump(Map<String, Bucket> buckets, String key, int threshold, long windowMillis) {
        var now = System.currentTimeMillis();
        var bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        bucket.evictExpired(now, windowMillis);
        bucket.add(now);
        return bucket.count() > threshold;
    }

    private void evictIfFull() {
        evict(accountBuckets);
        evict(ipBuckets);
    }

    private void evict(Map<String, Bucket> buckets) {
        if (buckets.size() <= maxEntries) return;
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext() && buckets.size() > maxEntries / 2) {
            it.next();
            it.remove();
        }
    }

    private static final class Bucket {
        private final java.util.ArrayDeque<Long> timestamps = new java.util.ArrayDeque<>();

        Bucket() {}

        void add(long ts) { timestamps.add(ts); }

        int count() { return timestamps.size(); }

        void evictExpired(long now, long windowMillis) {
            var cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
        }
    }

    record Result(boolean isBlocked) {}
}
