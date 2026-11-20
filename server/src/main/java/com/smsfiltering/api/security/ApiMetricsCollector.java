package com.smsfiltering.api.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gizlilik odaklı (PII içermeyen) sunucu çalışma zamanı metrik toplayıcısı.
 * Yalnızca anonim sayaçlar tutulur; IP, gönderen adı veya SMS içeriği asla saklanmaz.
 */
@Component
public class ApiMetricsCollector {

    private final Instant startedAt = Instant.now();
    private final AtomicLong totalLookups = new AtomicLong(0);
    private final AtomicLong totalSignals = new AtomicLong(0);
    private final AtomicLong totalKeywordSyncs = new AtomicLong(0);
    private final AtomicLong totalRateLimited = new AtomicLong(0);
    private final AtomicLong totalUnauthorizedSignals = new AtomicLong(0);

    public void recordLookup() {
        totalLookups.incrementAndGet();
    }

    public void recordSignal() {
        totalSignals.incrementAndGet();
    }

    public void recordKeywordSync() {
        totalKeywordSyncs.incrementAndGet();
    }

    public void recordRateLimited() {
        totalRateLimited.incrementAndGet();
    }

    public void recordUnauthorizedSignal() {
        totalUnauthorizedSignals.incrementAndGet();
    }

    public long getUptimeSeconds() {
        return Math.max(0L, Instant.now().getEpochSecond() - startedAt.getEpochSecond());
    }

    public long getTotalLookups() {
        return totalLookups.get();
    }

    public long getTotalSignals() {
        return totalSignals.get();
    }

    public long getTotalKeywordSyncs() {
        return totalKeywordSyncs.get();
    }

    public long getTotalRateLimited() {
        return totalRateLimited.get();
    }

    public long getTotalUnauthorizedSignals() {
        return totalUnauthorizedSignals.get();
    }
}
