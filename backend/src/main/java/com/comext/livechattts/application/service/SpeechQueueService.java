package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase;
import com.comext.livechattts.application.port.in.StatusUseCase;
import com.comext.livechattts.application.port.out.SpeechEngine;
import com.comext.livechattts.domain.ChatMessage;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SpeechQueueService implements AutoCloseable {
    private static final int HISTORY_CAPACITY = 100;
    private final ArrayBlockingQueue<QueuedMessage> queue;
    private final SpeechEngine speechEngine;
    private final SpeechSettingsUseCase settings;
    private final RuntimeDiagnostics diagnostics;
    private final SlidingWindowRateLimiter senderLimiter = new SlidingWindowRateLimiter(4, Duration.ofSeconds(10));
    private final SlidingWindowRateLimiter globalLimiter = new SlidingWindowRateLimiter(180, Duration.ofMinutes(1));
    private final AtomicBoolean speaking = new AtomicBoolean();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong messageIds = new AtomicLong();
    private final Object historyLock = new Object();
    private final Deque<HistoryEntry> history = new ArrayDeque<>(HISTORY_CAPACITY);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("speech-worker-", 0).factory());

    public SpeechQueueService(int capacity, SpeechEngine speechEngine, SpeechSettingsUseCase settings, RuntimeDiagnostics diagnostics) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.speechEngine = speechEngine;
        this.settings = settings;
        this.diagnostics = diagnostics;
        worker.submit(this::consume);
    }

    public boolean submit(ChatMessage message) {
        long id = messageIds.incrementAndGet();
        if (!senderLimiter.tryAcquire(message.author()) || !globalLimiter.tryAcquire("all")) {
            rejected.incrementAndGet();
            addHistory(id, message, "REJECTED");
            return false;
        }
        addHistory(id, message, "QUEUED");
        if (!queue.offer(new QueuedMessage(id, message))) {
            dropped.incrementAndGet();
            updateState(id, "DROPPED");
            return false;
        }
        accepted.incrementAndGet();
        return true;
    }

    private void consume() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                QueuedMessage queued = queue.take();
                ChatMessage message = queued.message();
                SpeechSettingsUseCase.Settings current = settings.settings();
                if (current.voiceId().isBlank()) { updateState(queued.id(), "FAILED"); continue; }
                speaking.set(true);
                updateState(queued.id(), "SPEAKING");
                try {
                    speechEngine.speak(message.author() + " dice: " + message.text(), current.voiceId(), current.speechRate(), current.audioOutputId());
                    updateState(queued.id(), "SPOKEN");
                } catch (Exception exception) {
                    diagnostics.record("SAPI", exception);
                    updateState(queued.id(), "FAILED");
                } finally {
                    speaking.set(false);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isSpeaking() { return speaking.get(); }
    public int depth() { return queue.size(); }
    public long accepted() { return accepted.get(); }
    public long dropped() { return dropped.get(); }
    public long rejected() { return rejected.get(); }
    public List<StatusUseCase.SpeechMessage> messages() {
        synchronized (historyLock) {
            return history.stream().map(entry -> new StatusUseCase.SpeechMessage(entry.id(), entry.message().author(), entry.message().text(), entry.message().receivedAt().toString(), entry.state())).toList();
        }
    }

    private void addHistory(long id, ChatMessage message, String state) {
        synchronized (historyLock) {
            if (history.size() == HISTORY_CAPACITY) history.removeFirst();
            history.addLast(new HistoryEntry(id, message, state));
        }
    }

    private void updateState(long id, String state) {
        synchronized (historyLock) {
            List<HistoryEntry> updated = new ArrayList<>(history.size());
            for (HistoryEntry entry : history) updated.add(entry.id() == id ? new HistoryEntry(entry.id(), entry.message(), state) : entry);
            history.clear();
            history.addAll(updated);
        }
    }

    @Override public void close() { worker.shutdownNow(); }

    private record QueuedMessage(long id, ChatMessage message) { }
    private record HistoryEntry(long id, ChatMessage message, String state) { }
}
