package com.comext.livechattts.application.service;

import com.comext.livechattts.application.port.in.SpeechSettingsUseCase;
import com.comext.livechattts.application.port.in.StatusUseCase;
import com.comext.livechattts.application.port.out.AudioCuePlayer;
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
    private final AudioCuePlayer giftCuePlayer;
    private final SpeechSettingsUseCase settings;
    private final RuntimeDiagnostics diagnostics;
    private final SlidingWindowRateLimiter senderLimiter = new SlidingWindowRateLimiter(10, Duration.ofSeconds(10));
    private final SlidingWindowRateLimiter globalLimiter = new SlidingWindowRateLimiter(180, Duration.ofMinutes(1));
    private final AtomicBoolean speaking = new AtomicBoolean();
    private final AtomicBoolean giftCueEnabled = new AtomicBoolean(true);
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong messageIds = new AtomicLong();
    private final Object historyLock = new Object();
    private final Deque<HistoryEntry> history = new ArrayDeque<>(HISTORY_CAPACITY);
    /**
     * A dedicated platform thread keeps a predictable OS scheduling priority.
     * Virtual threads do not provide a useful per-task priority on Windows.
     */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "speech-worker");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    public SpeechQueueService(int capacity, SpeechEngine speechEngine, AudioCuePlayer giftCuePlayer, SpeechSettingsUseCase settings, RuntimeDiagnostics diagnostics) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.speechEngine = speechEngine;
        this.giftCuePlayer = giftCuePlayer;
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

    /** Removes messages that have not started speaking while retaining their history entries. */
    public int discardPending() {
        List<QueuedMessage> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(message -> updateState(message.id(), "CANCELLED"));
        return pending.size();
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
                    String spokenAuthor = TextSanitizer.sanitizeAuthorForSpeech(message.author());
                    String spokenText = message.type() == ChatMessage.Type.CHAT ? spokenAuthor + " dice: " + message.text() : spokenAuthor + " " + message.text();
                    speechEngine.speak(spokenText, current.voiceId(), current.speechRate(), current.audioOutputId());
                    playGiftCue(message);
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

    private void playGiftCue(ChatMessage message) {
        if (message.type() != ChatMessage.Type.GIFT || !giftCueEnabled.get()) return;
        try {
            giftCuePlayer.play();
        } catch (Exception exception) {
            if (giftCueEnabled.compareAndSet(true, false)) diagnostics.record("Gift alert", exception);
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

    @Override public void close() {
        discardPending();
        worker.shutdownNow();
        try { giftCuePlayer.close(); }
        catch (Exception ignored) { }
        if (speechEngine instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (Exception ignored) { }
        }
    }

    private record QueuedMessage(long id, ChatMessage message) { }
    private record HistoryEntry(long id, ChatMessage message, String state) { }
}
