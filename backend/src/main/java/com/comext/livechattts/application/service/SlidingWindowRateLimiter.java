package com.comext.livechattts.application.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Bounded, synchronized limiter; old sender entries are opportunistically removed. */
public final class SlidingWindowRateLimiter {
    private final int maxEvents;
    private final long windowMillis;
    private final Map<String, Deque<Long>> events = new HashMap<>();

    public SlidingWindowRateLimiter(int maxEvents, Duration window) {
        this.maxEvents = maxEvents;
        this.windowMillis = window.toMillis();
    }

    public synchronized boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (events.size() > 4_000) events.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        Deque<Long> timestamps = events.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        purge(timestamps, now);
        if (timestamps.size() >= maxEvents) return false;
        timestamps.addLast(now);
        return true;
    }

    private boolean expired(Deque<Long> timestamps, long now) {
        purge(timestamps, now);
        return timestamps.isEmpty();
    }

    private void purge(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) timestamps.removeFirst();
    }
}
