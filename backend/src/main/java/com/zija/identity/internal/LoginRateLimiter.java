package com.zija.identity.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录速率限制器，防止暴力破解攻击。
 * <p>
 * 基于滑动窗口算法，同时对用户名和 IP 两个维度进行限流：
 * <ul>
 *   <li>账户维度：同一用户名在窗口期内连续失败达到阈值后被封锁</li>
 *   <li>IP 维度：同一 IP 在窗口期内连续失败达到阈值后被封锁</li>
 * </ul>
 * 使用基于 LRU 策略的内存存储，超过最大条目数时自动淘汰未封锁的记录。
 */
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

    @Autowired
    LoginRateLimiter(
            @Value("${zija.login.rate-limit.account-threshold:5}") int accountThreshold,
            @Value("${zija.login.rate-limit.account-window-minutes:5}") int accountWindowMinutes,
            @Value("${zija.login.rate-limit.ip-threshold:50}") int ipThreshold,
            @Value("${zija.login.rate-limit.ip-window-minutes:5}") int ipWindowMinutes,
            @Value("${zija.login.rate-limit.max-entries:1000}") int maxEntries
    ) {
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

    /**
     * 检查指定用户名和 IP 是否被限流，被限流时抛出异常。
     *
     * @param normalizedUsername 归一化用户名
     * @param ip                客户端 IP 地址
     * @throws LoginRateLimitedException 如果用户名或 IP 已被封锁
     */
    synchronized void checkAllowed(String normalizedUsername, String ip) {
        var now = clock.millis();
        cleanup(now);
        if (isBlocked(accountBuckets.get(normalizedUsername), now)
                || isBlocked(ipBuckets.get(ip), now)) {
            throw new LoginRateLimitedException("rate limited");
        }
    }

    /**
     * 记录一次登录失败，更新用户名和 IP 的失败计数，达到阈值时封锁并抛出异常。
     *
     * @param normalizedUsername 归一化用户名
     * @param ip                客户端 IP 地址
     * @return 限流结果
     * @throws LoginRateLimitedException 如果因本次失败触发封锁
     */
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

    /**
     * 记录一次登录成功，清除该用户名的失败计数。
     *
     * @param normalizedUsername 归一化用户名
     */
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
